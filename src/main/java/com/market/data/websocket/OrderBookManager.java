package com.market.data.websocket;

import com.market.data.domain.OrderBook;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrderBookManager {
    private final ConcurrentHashMap<String, OrderBook> orderBooks;
    private final MeterRegistry meterRegistry;

    public OrderBookManager(MeterRegistry meterRegistry) {
        this.orderBooks = new ConcurrentHashMap<>();
        this.meterRegistry = meterRegistry;

        // Initialize order books for known symbols
        orderBooks.put("BTCUSDT", OrderBook.empty("BTCUSDT"));
        orderBooks.put("ETHUSDT", OrderBook.empty("ETHUSDT"));
    }

    public OrderBook getOrderBook(String symbol) {
        return orderBooks.computeIfAbsent(symbol, k -> OrderBook.empty(k));
    }

    public void requestSnapshot(String symbol) {
        log.warn("Snapshot requested for symbol: {}", symbol);
        // In production, this would trigger a REST API call to fetch the snapshot
        meterRegistry.counter("orderbook.snapshot.requests", Tags.of("symbol", symbol)).increment();
    }

    public Collection<OrderBook> getAllOrderBooks() {
        return orderBooks.values();
    }

    public int getOrderBookCount() {
        return orderBooks.size();
    }

    public long getTotalDepth() {
        return orderBooks.values().stream()
            .mapToLong(OrderBook::getDepth)
            .sum();
    }

    public void reset(String symbol) {
        orderBooks.put(symbol, OrderBook.empty(symbol));
        log.info("Order book reset for symbol: {}", symbol);
    }

    public void resetAll() {
        orderBooks.forEach((symbol, book) -> reset(symbol));
        log.info("All order books reset");
    }
}
