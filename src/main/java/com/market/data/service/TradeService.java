package com.market.data.service;

import com.market.data.domain.Trade;
import java.time.Instant;
import java.util.List;

public interface TradeService {
    void recordTrade(Trade trade);

    List<Trade> getRecentTrades(String symbol, int limit);

    Trade getLatestTrade(String symbol);

    long getTotalTradesCount(String symbol);

    List<Trade> getTradesSince(String symbol, Instant since);

    double getAverageTradeSize(String symbol, int sampleSize);
}
