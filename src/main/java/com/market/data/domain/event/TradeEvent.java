package com.market.data.domain.event;

import com.market.data.domain.Trade;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeEvent extends MarketEvent {
    private Trade trade;

    public TradeEvent(String symbol, Instant timestamp, long sequenceNumber, Trade trade) {
        super(symbol, timestamp, sequenceNumber);
        this.trade = trade;
    }
}
