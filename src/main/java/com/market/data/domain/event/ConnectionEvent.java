package com.market.data.domain.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectionEvent {
    public enum Status {
        CONNECTING, CONNECTED, DISCONNECTED, RECONNECTING, FAILED
    }

    private String eventId;
    private Status status;
    private Instant timestamp;
    private String reason;
    private int reconnectAttempt;

    public ConnectionEvent(Status status, String reason, int reconnectAttempt) {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.status = status;
        this.timestamp = Instant.now();
        this.reason = reason;
        this.reconnectAttempt = reconnectAttempt;
    }
}
