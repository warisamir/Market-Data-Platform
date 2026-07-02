package com.market.data.controller;

import com.market.data.dto.HealthResponse;
import com.market.data.event.EventBus;
import com.market.data.websocket.BinanceWebSocketClient;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@Slf4j
public class HealthController {
    private final BinanceWebSocketClient wsClient;
    private final EventBus eventBus;
    private final MeterRegistry meterRegistry;
    private final long startTime = System.currentTimeMillis();

    public HealthController(
        BinanceWebSocketClient wsClient,
        EventBus eventBus,
        MeterRegistry meterRegistry) {
        this.wsClient = wsClient;
        this.eventBus = eventBus;
        this.meterRegistry = meterRegistry;
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        HealthResponse.Status status = determineStatus();

        Map<String, Boolean> services = new HashMap<>();
        services.put("websocket", wsClient.isConnected());
        services.put("eventbus", eventBus.isRunning());

        Map<String, String> details = new HashMap<>();
        details.put("ws_connection_duration_ms", String.valueOf(wsClient.getConnectionDurationMs()));
        details.put("ws_last_message_ms_ago", String.valueOf(wsClient.getTimeSinceLastMessageMs()));
        details.put("eventbus_queue_size", String.valueOf(eventBus.getQueueSize()));
        details.put("eventbus_subscribers", String.valueOf(eventBus.getSubscriberCount("BTCUSDT")));

        long uptime = System.currentTimeMillis() - startTime;

        return ResponseEntity.ok(HealthResponse.builder()
            .status(status)
            .timestamp(Instant.now())
            .services(services)
            .details(details)
            .uptime(uptime)
            .messagesProcessed(getMessageCount())
            .build());
    }

    private HealthResponse.Status determineStatus() {
        if (wsClient.isConnected() && eventBus.isRunning()) {
            return HealthResponse.Status.UP;
        } else if (eventBus.isRunning()) {
            return HealthResponse.Status.DEGRADED;
        } else {
            return HealthResponse.Status.DOWN;
        }
    }

    private long getMessageCount() {
        Double trades = meterRegistry.find("trades.received").counters().stream()
            .mapToDouble(c -> c.count()).sum();
        Double tickers = meterRegistry.find("tickers.received").counters().stream()
            .mapToDouble(c -> c.count()).sum();
        Double updates = meterRegistry.find("orderbook.updates").counters().stream()
            .mapToDouble(c -> c.count()).sum();
        return Math.round(trades + tickers + updates);
    }
}
