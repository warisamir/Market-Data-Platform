package com.market.data.domain.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class MarketEvent {
    private String eventId;
    private String symbol;
    private Instant timestamp;
    private long sequenceNumber;

    protected MarketEvent(String symbol, Instant timestamp, long sequenceNumber) {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.sequenceNumber = sequenceNumber;
    }
}
