package com.market.data.domain.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
public class ConnectionEvent extends MarketEvent {
    public enum Status {
        CONNECTING, CONNECTED, DISCONNECTED, RECONNECTING, FAILED
    }

    private Status status;
    private String reason;
    private int reconnectAttempt;

    public ConnectionEvent(Status status, String reason, int reconnectAttempt) {
        super("SYSTEM", Instant.now(), -1);
        this.status = status;
        this.reason = reason;
        this.reconnectAttempt = reconnectAttempt;
    }
}
