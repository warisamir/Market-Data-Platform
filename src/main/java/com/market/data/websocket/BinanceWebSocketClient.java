package com.market.data.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.market.data.config.WebSocketConfig;
import com.market.data.domain.event.ConnectionEvent;
import com.market.data.event.EventBus;
import com.market.data.exception.WebSocketException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Component
@Slf4j
public class BinanceWebSocketClient extends AbstractWebSocketHandler {
    private final WebSocketConfig wsConfig;
    private final EventBus eventBus;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final MessageParser messageParser;

    private WebSocketSession currentSession;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private long lastConnectTime = 0;
    private long lastMessageTime = 0;

    public BinanceWebSocketClient(
        WebSocketConfig wsConfig,
        EventBus eventBus,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry,
        MessageParser messageParser) {
        this.wsConfig = wsConfig;
        this.eventBus = eventBus;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.messageParser = messageParser;
    }

    public synchronized void connect() throws WebSocketException {
        if (connected.get()) {
            log.warn("Already connected to WebSocket");
            return;
        }

        try {
            log.info("Connecting to WebSocket: {}", wsConfig.getUrl());
            eventBus.publish(new ConnectionEvent(
                ConnectionEvent.Status.CONNECTING,
                "Initiating connection",
                reconnectAttempts.get()
            ));

            WebSocketClient client = new StandardWebSocketClient();
            WebSocketSession session = client.execute(this, wsConfig.getUrl()).get();

            this.currentSession = session;
            this.connected.set(true);
            this.lastConnectTime = System.currentTimeMillis();
            this.reconnectAttempts.set(0);

            log.info("Connected to WebSocket successfully");
            eventBus.publish(new ConnectionEvent(
                ConnectionEvent.Status.CONNECTED,
                "Connection established",
                0
            ));

            meterRegistry.counter("websocket.connections.successful").increment();
        } catch (Exception e) {
            log.error("Failed to connect to WebSocket", e);
            connected.set(false);
            handleConnectionError(e);
            throw new WebSocketException("Failed to connect to WebSocket", e);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established: {}", session.getId());
        connected.set(true);
        reconnectAttempts.set(0);
        lastConnectTime = System.currentTimeMillis();

        subscribeToStreams();
        startHeartbeat();
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        lastMessageTime = System.currentTimeMillis();

        if (!(message instanceof TextMessage textMessage)) {
            log.warn("Received non-text message");
            return;
        }

        String payload = textMessage.getPayload();
        try {
            JsonNode node = objectMapper.readTree(payload);
            messageParser.parse(node);
            meterRegistry.counter("websocket.messages.received").increment();
        } catch (Exception e) {
            log.error("Error parsing WebSocket message: {}", payload, e);
            meterRegistry.counter("websocket.messages.parse.error").increment();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error", exception);
        connected.set(false);
        meterRegistry.counter("websocket.transport.errors").increment();
        scheduleReconnect();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus closeStatus) {
        log.info("WebSocket connection closed: {}", closeStatus);
        connected.set(false);
        currentSession = null;

        eventBus.publish(new ConnectionEvent(
            ConnectionEvent.Status.DISCONNECTED,
            closeStatus.getReason(),
            reconnectAttempts.get()
        ));

        meterRegistry.counter("websocket.connections.closed").increment();
        scheduleReconnect();
    }

    public synchronized void disconnect() {
        if (currentSession != null && currentSession.isOpen()) {
            try {
                currentSession.close();
                log.info("WebSocket disconnected");
            } catch (Exception e) {
                log.error("Error closing WebSocket", e);
            }
        }
        connected.set(false);
    }

    public boolean isConnected() {
        return connected.get() && currentSession != null && currentSession.isOpen();
    }

    public long getConnectionDurationMs() {
        return System.currentTimeMillis() - lastConnectTime;
    }

    public long getTimeSinceLastMessageMs() {
        return System.currentTimeMillis() - lastMessageTime;
    }

    private synchronized void subscribeToStreams() {
        if (!isConnected()) {
            log.warn("Cannot subscribe: not connected");
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(new SubscriptionRequest(
                "SUBSCRIBE",
                new String[]{
                    "btcusdt@trade",
                    "btcusdt@depth@100ms",
                    "btcusdt@ticker",
                    "ethusdt@trade",
                    "ethusdt@depth@100ms",
                    "ethusdt@ticker"
                }
            ));

            currentSession.sendMessage(new TextMessage(payload));
            log.info("Subscribed to streams: BTCUSDT, ETHUSDT");
            meterRegistry.counter("websocket.subscriptions").increment(2);
        } catch (Exception e) {
            log.error("Error subscribing to streams", e);
            meterRegistry.counter("websocket.subscriptions.error").increment();
        }
    }

    private void startHeartbeat() {
        // Heartbeat is handled by the message parser monitoring message frequency
    }

    private void scheduleReconnect() {
        if (!connected.get() && reconnectAttempts.get() < wsConfig.getMaxReconnectAttempts()) {
            int attempt = reconnectAttempts.incrementAndGet();
            long delayMs = calculateBackoff(attempt);

            log.info("Scheduling reconnect attempt {} after {}ms", attempt, delayMs);
            eventBus.publish(new ConnectionEvent(
                ConnectionEvent.Status.RECONNECTING,
                "Scheduled reconnect attempt " + attempt,
                attempt
            ));

            new Thread(() -> {
                try {
                    Thread.sleep(delayMs);
                    connect();
                } catch (InterruptedException e) {
                    log.debug("Reconnect interrupted");
                    Thread.currentThread().interrupt();
                } catch (WebSocketException e) {
                    log.error("Reconnect failed", e);
                    scheduleReconnect();
                }
            }, "WebSocketReconnect-" + attempt).start();
        } else if (reconnectAttempts.get() >= wsConfig.getMaxReconnectAttempts()) {
            log.error("Max reconnect attempts reached");
            eventBus.publish(new ConnectionEvent(
                ConnectionEvent.Status.FAILED,
                "Max reconnect attempts exceeded",
                reconnectAttempts.get()
            ));
            meterRegistry.counter("websocket.reconnect.failed").increment();
        }
    }

    private long calculateBackoff(int attempt) {
        long delayMs = Math.min(
            1000L * (long) Math.pow(2, attempt - 1),
            wsConfig.getMaxReconnectDelayMs()
        );
        // Add jitter: ±10% of delay
        return delayMs + (long) (Math.random() * delayMs * 0.2 - delayMs * 0.1);
    }

    private void handleConnectionError(Exception e) {
        meterRegistry.counter("websocket.connection.errors").increment();
        scheduleReconnect();
    }

    record SubscriptionRequest(String method, String[] params) {}
}
