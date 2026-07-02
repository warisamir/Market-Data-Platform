package com.market.data.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.TreeMap;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderBook {
    private String symbol;
    private NavigableMap<BigDecimal, OrderBookEntry> bids;
    private NavigableMap<BigDecimal, OrderBookEntry> asks;
    private long lastUpdateId;
    private long snapshotUpdateId;
    private Instant snapshotTime;
    private String checksumValue;
    private long messageCount;

    public static OrderBook empty(String symbol) {
        return OrderBook.builder()
            .symbol(symbol)
            .bids(new TreeMap<>(Collections.reverseOrder()))
            .asks(new TreeMap<>())
            .lastUpdateId(0)
            .snapshotUpdateId(0)
            .snapshotTime(Instant.now())
            .messageCount(0)
            .build();
    }

    public BigDecimal getMidPrice() {
        if (bids.isEmpty() || asks.isEmpty()) {
            return null;
        }
        BigDecimal bestBid = bids.firstKey();
        BigDecimal bestAsk = asks.firstKey();
        return bestBid.add(bestAsk).divide(BigDecimal.valueOf(2));
    }

    public BigDecimal getSpread() {
        if (bids.isEmpty() || asks.isEmpty()) {
            return null;
        }
        return asks.firstKey().subtract(bids.firstKey());
    }

    public long getDepth() {
        return bids.size() + asks.size();
    }

    public boolean requiresSnapshot() {
        return messageCount > 10000 || (System.currentTimeMillis() - snapshotTime.toEpochMilli()) > 3600000;
    }
}
