package com.market.data.dto;

import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthResponse {
    public enum Status {
        UP, DEGRADED, DOWN
    }

    private Status status;
    private Instant timestamp;
    private Map<String, Boolean> services;
    private Map<String, String> details;
    private long uptime;
    private long messagesProcessed;
}
