package com.market.data.domain.event;

import com.market.data.domain.Ticker;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TickerEvent extends MarketEvent {
    private Ticker ticker;

    public TickerEvent(String symbol, Instant timestamp, long sequenceNumber, Ticker ticker) {
        super(symbol, timestamp, sequenceNumber);
        this.ticker = ticker;
    }
}
