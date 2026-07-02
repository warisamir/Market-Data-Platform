package com.market.data.service;

import com.market.data.domain.Trade;
import com.market.data.event.EventBus;
import com.market.data.service.impl.TradeServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {
    @Mock
    private EventBus eventBus;

    private TradeServiceImpl tradeService;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        tradeService = new TradeServiceImpl(eventBus, meterRegistry);
    }

    @Test
    void testRecordTrade() {
        Trade trade = Trade.builder()
            .symbol("BTCUSDT")
            .price(new BigDecimal("99500.00"))
            .quantity(new BigDecimal("0.5"))
            .timestamp(Instant.now())
            .exchangeTradeId(123L)
            .buyerIsMaker(true)
            .receivedAt(System.currentTimeMillis())
            .build();

        tradeService.recordTrade(trade);

        assertEquals(1, tradeService.getTotalTradesCount("BTCUSDT"));
        assertEquals(trade, tradeService.getLatestTrade("BTCUSDT"));
    }

    @Test
    void testGetRecentTrades() {
        Instant now = Instant.now();

        for (int i = 0; i < 5; i++) {
            Trade trade = Trade.builder()
                .symbol("BTCUSDT")
                .price(new BigDecimal("99500.00").add(BigDecimal.valueOf(i)))
                .quantity(new BigDecimal("0.5"))
                .timestamp(now.plusSeconds(i))
                .exchangeTradeId(100L + i)
                .buyerIsMaker(i % 2 == 0)
                .receivedAt(System.currentTimeMillis())
                .build();

            tradeService.recordTrade(trade);
        }

        List<Trade> trades = tradeService.getRecentTrades("BTCUSDT", 3);
        assertEquals(3, trades.size());

        // Most recent first
        assertEquals(104L, trades.get(0).getExchangeTradeId());
        assertEquals(103L, trades.get(1).getExchangeTradeId());
        assertEquals(102L, trades.get(2).getExchangeTradeId());
    }

    @Test
    void testGetLatestTrade() {
        assertNull(tradeService.getLatestTrade("BTCUSDT"));

        Trade trade1 = Trade.builder()
            .symbol("BTCUSDT")
            .price(new BigDecimal("99500.00"))
            .quantity(new BigDecimal("0.5"))
            .timestamp(Instant.now())
            .exchangeTradeId(1L)
            .buyerIsMaker(true)
            .receivedAt(System.currentTimeMillis())
            .build();

        tradeService.recordTrade(trade1);
        assertEquals(trade1, tradeService.getLatestTrade("BTCUSDT"));

        Trade trade2 = Trade.builder()
            .symbol("BTCUSDT")
            .price(new BigDecimal("99510.00"))
            .quantity(new BigDecimal("0.3"))
            .timestamp(Instant.now().plusSeconds(1))
            .exchangeTradeId(2L)
            .buyerIsMaker(false)
            .receivedAt(System.currentTimeMillis())
            .build();

        tradeService.recordTrade(trade2);
        assertEquals(trade2, tradeService.getLatestTrade("BTCUSDT"));
    }

    @Test
    void testGetTradesSince() {
        Instant baseTime = Instant.now();

        for (int i = 0; i < 5; i++) {
            Trade trade = Trade.builder()
                .symbol("BTCUSDT")
                .price(new BigDecimal("99500.00"))
                .quantity(new BigDecimal("0.5"))
                .timestamp(baseTime.plusSeconds(i))
                .exchangeTradeId(100L + i)
                .buyerIsMaker(true)
                .receivedAt(System.currentTimeMillis())
                .build();

            tradeService.recordTrade(trade);
        }

        Instant cutoff = baseTime.plusSeconds(2);
        List<Trade> trades = tradeService.getTradesSince("BTCUSDT", cutoff);

        // Should get trades at seconds 3, 4 (after cutoff)
        assertEquals(2, trades.size());
    }

    @Test
    void testGetAverageTradeSize() {
        for (int i = 1; i <= 5; i++) {
            Trade trade = Trade.builder()
                .symbol("BTCUSDT")
                .price(new BigDecimal("99500.00"))
                .quantity(new BigDecimal(i))  // 1, 2, 3, 4, 5
                .timestamp(Instant.now())
                .exchangeTradeId(100L + i)
                .buyerIsMaker(true)
                .receivedAt(System.currentTimeMillis())
                .build();

            tradeService.recordTrade(trade);
        }

        double averageSize = tradeService.getAverageTradeSize("BTCUSDT", 5);
        // Average of 1, 2, 3, 4, 5 = 3
        assertEquals(3.0, averageSize, 0.01);
    }

    @Test
    void testMultipleSymbols() {
        Trade btcTrade = Trade.builder()
            .symbol("BTCUSDT")
            .price(new BigDecimal("99500.00"))
            .quantity(new BigDecimal("0.5"))
            .timestamp(Instant.now())
            .exchangeTradeId(1L)
            .buyerIsMaker(true)
            .receivedAt(System.currentTimeMillis())
            .build();

        Trade ethTrade = Trade.builder()
            .symbol("ETHUSDT")
            .price(new BigDecimal("3500.00"))
            .quantity(new BigDecimal("5.0"))
            .timestamp(Instant.now())
            .exchangeTradeId(2L)
            .buyerIsMaker(false)
            .receivedAt(System.currentTimeMillis())
            .build();

        tradeService.recordTrade(btcTrade);
        tradeService.recordTrade(ethTrade);

        assertEquals(1, tradeService.getTotalTradesCount("BTCUSDT"));
        assertEquals(1, tradeService.getTotalTradesCount("ETHUSDT"));
        assertEquals(btcTrade, tradeService.getLatestTrade("BTCUSDT"));
        assertEquals(ethTrade, tradeService.getLatestTrade("ETHUSDT"));
    }

    @Test
    void testRecordNullTrade() {
        // Should not throw exception
        tradeService.recordTrade(null);
        assertEquals(0, tradeService.getTotalTradesCount("BTCUSDT"));
    }

    @Test
    void testCacheLimitEnforced() {
        // Record more than MAX_TRADES_CACHE
        for (int i = 0; i < 1100; i++) {
            Trade trade = Trade.builder()
                .symbol("BTCUSDT")
                .price(new BigDecimal("99500.00"))
                .quantity(new BigDecimal("0.5"))
                .timestamp(Instant.now())
                .exchangeTradeId(100L + i)
                .buyerIsMaker(true)
                .receivedAt(System.currentTimeMillis())
                .build();

            tradeService.recordTrade(trade);
        }

        // Should keep only MAX_TRADES_CACHE (1000)
        assertTrue(tradeService.getTotalTradesCount("BTCUSDT") <= 1000);
    }
}
