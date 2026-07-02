package com.market.data.startup;

import com.market.data.event.EventBus;
import com.market.data.exception.WebSocketException;
import com.market.data.websocket.BinanceWebSocketClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ApplicationStartup {
    private final EventBus eventBus;
    private final BinanceWebSocketClient wsClient;

    public ApplicationStartup(EventBus eventBus, BinanceWebSocketClient wsClient) {
        this.eventBus = eventBus;
        this.wsClient = wsClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Starting Market Data Platform");

        try {
            // Start the event bus
            eventBus.start();
            log.info("Event bus started");

            // Connect to WebSocket
            wsClient.connect();
            log.info("WebSocket connected");

            log.info("Market Data Platform ready");
        } catch (WebSocketException e) {
            log.error("Failed to start application", e);
            // System will retry connection via reconnection logic
        } catch (Exception e) {
            log.error("Unexpected error during startup", e);
        }
    }
}
