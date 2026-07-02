package com.market.data.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "websocket")
@Data
public class WebSocketConfig {
    private String url = "wss://stream.binance.com:9443/ws";
    private long connectionTimeoutMs = 10000;
    private long readTimeoutMs = 30000;
    private long heartbeatIntervalMs = 30000;
    private long maxReconnectDelayMs = 60000;
    private int maxReconnectAttempts = 10;
    private boolean enableChecksum = true;
}
