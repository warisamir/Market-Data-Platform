package com.market.data.domain.event;

import com.market.data.domain.OrderBook;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderBookEvent extends MarketEvent {
    private OrderBook orderBook;
    private boolean isSnapshot;

    public OrderBookEvent(String symbol, Instant timestamp, long sequenceNumber, OrderBook orderBook, boolean isSnapshot) {
        super(symbol, timestamp, sequenceNumber);
        this.orderBook = orderBook;
        this.isSnapshot = isSnapshot;
    }
}
