package com.market.data.service.impl;

import com.market.data.domain.Trade;
import com.market.data.domain.event.TradeEvent;
import com.market.data.event.EventBus;
import com.market.data.event.EventConsumer;
import com.market.data.domain.event.MarketEvent;
import com.market.data.service.TradeService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TradeServiceImpl implements TradeService, EventConsumer {
    private static final int MAX_TRADES_CACHE = 1000;

    private final EventBus eventBus;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Trade>> recentTrades;
    private final ConcurrentHashMap<String, Trade> latestTrades;

    public TradeServiceImpl(EventBus eventBus, MeterRegistry meterRegistry) {
        this.eventBus = eventBus;
        this.meterRegistry = meterRegistry;
        this.recentTrades = new ConcurrentHashMap<>();
        this.latestTrades = new ConcurrentHashMap<>();

        // Initialize for known symbols
        recentTrades.put("BTCUSDT", new CopyOnWriteArrayList<>());
        recentTrades.put("ETHUSDT", new CopyOnWriteArrayList<>());

        // Subscribe to trade events
        eventBus.subscribe("BTCUSDT", this);
        eventBus.subscribe("ETHUSDT", this);
    }

    @Override
    public void recordTrade(Trade trade) {
        if (trade == null || trade.getSymbol() == null) {
            log.warn("Invalid trade: {}", trade);
            return;
        }

        String symbol = trade.getSymbol();
        CopyOnWriteArrayList<Trade> trades = recentTrades.computeIfAbsent(
            symbol,
            k -> new CopyOnWriteArrayList<>()
        );

        trades.add(0, trade);

        // Keep only recent trades
        if (trades.size() > MAX_TRADES_CACHE) {
            trades.remove(trades.size() - 1);
        }

        latestTrades.put(symbol, trade);
        meterRegistry.counter("trades.recorded", Tags.of("symbol", symbol)).increment();

        log.debug("Trade recorded: {} @ {} {}", symbol, trade.getPrice(), trade.getQuantity());
    }

    @Override
    public List<Trade> getRecentTrades(String symbol, int limit) {
        CopyOnWriteArrayList<Trade> trades = recentTrades.get(symbol);
        if (trades == null || trades.isEmpty()) {
            return Collections.emptyList();
        }

        List<Trade> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, trades.size()); i++) {
            result.add(trades.get(i));
        }
        return result;
    }

    @Override
    public Trade getLatestTrade(String symbol) {
        return latestTrades.get(symbol);
    }

    @Override
    public long getTotalTradesCount(String symbol) {
        CopyOnWriteArrayList<Trade> trades = recentTrades.get(symbol);
        return trades == null ? 0 : trades.size();
    }

    @Override
    public List<Trade> getTradesSince(String symbol, Instant since) {
        CopyOnWriteArrayList<Trade> trades = recentTrades.get(symbol);
        if (trades == null) {
            return Collections.emptyList();
        }

        List<Trade> result = new ArrayList<>();
        for (Trade trade : trades) {
            if (trade.getTimestamp().isAfter(since)) {
                result.add(trade);
            }
        }
        return result;
    }

    @Override
    public double getAverageTradeSize(String symbol, int sampleSize) {
        List<Trade> trades = getRecentTrades(symbol, sampleSize);
        if (trades.isEmpty()) {
            return 0.0;
        }

        return trades.stream()
            .map(Trade::getQuantity)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
            .doubleValue() / trades.size();
    }

    @Override
    public void onEvent(MarketEvent event) {
        if (event instanceof TradeEvent tradeEvent) {
            recordTrade(tradeEvent.getTrade());
        }
    }

    @Override
    public String getConsumerId() {
        return "TradeService";
    }

    @Override
    public String getSymbol() {
        return null; // Listens to all symbols via subscribe calls
    }
}
