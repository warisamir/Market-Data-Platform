package com.market.data.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.market.data.domain.OrderBook;
import com.market.data.domain.OrderBookEntry;
import com.market.data.domain.Ticker;
import com.market.data.domain.Trade;
import com.market.data.domain.event.OrderBookEvent;
import com.market.data.domain.event.TickerEvent;
import com.market.data.domain.event.TradeEvent;
import com.market.data.event.EventBus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MessageParser {
    private final EventBus eventBus;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final OrderBookManager orderBookManager;

    private long messageSequence = 0;

    public MessageParser(
        EventBus eventBus,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry,
        OrderBookManager orderBookManager) {
        this.eventBus = eventBus;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.orderBookManager = orderBookManager;
    }

    public void parse(JsonNode node) {
        try {
            String eventType = node.get("e").asText();

            long eventTime = node.get("E").asLong();
            String symbol = node.get("s").asText().toUpperCase();
            long sequence = messageSequence++;

            switch (eventType) {
                case "trade" -> handleTradeEvent(node, symbol, eventTime, sequence);
                case "depthUpdate" -> handleDepthUpdate(node, symbol, eventTime, sequence);
                case "24hrTicker" -> handleTickerEvent(node, symbol, eventTime, sequence);
                default -> log.debug("Unknown event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error parsing message", e);
            meterRegistry.counter("parser.error").increment();
        }
    }

    private void handleTradeEvent(JsonNode node, String symbol, long eventTime, long sequence) {
        try {
            Trade trade = Trade.builder()
                .symbol(symbol)
                .price(new BigDecimal(node.get("p").asText()))
                .quantity(new BigDecimal(node.get("q").asText()))
                .timestamp(Instant.ofEpochMilli(eventTime))
                .exchangeTradeId(node.get("t").asLong())
                .buyerOrderId(node.get("b").asText())
                .sellerOrderId(node.get("a").asText())
                .buyerIsMaker(node.get("m").asBoolean())
                .receivedAt(System.currentTimeMillis())
                .build();

            TradeEvent event = new TradeEvent(
                symbol,
                Instant.ofEpochMilli(eventTime),
                sequence,
                trade
            );

            eventBus.publish(event);
            meterRegistry.counter("trades.received", Tags.of("symbol", symbol)).increment();
        } catch (Exception e) {
            log.error("Error parsing trade event", e);
            meterRegistry.counter("parser.trade.error", Tags.of("symbol", symbol)).increment();
        }
    }

    private void handleDepthUpdate(JsonNode node, String symbol, long eventTime, long sequence) {
        try {
            long updateId = node.get("u").asLong();
            long firstUpdateId = node.get("U").asLong();
            long lastUpdateId = node.get("u").asLong();

            OrderBook orderBook = orderBookManager.getOrderBook(symbol);

            // Check if this is the first update (snapshot required)
            if (firstUpdateId <= orderBook.getLastUpdateId() + 1) {
                // Apply incremental update
                applyDepthUpdate(orderBook, node);
                orderBook.setLastUpdateId(updateId);
                orderBook.setMessageCount(orderBook.getMessageCount() + 1);

                OrderBookEvent event = new OrderBookEvent(
                    symbol,
                    Instant.ofEpochMilli(eventTime),
                    sequence,
                    orderBook,
                    false
                );

                eventBus.publish(event);
                meterRegistry.counter("orderbook.updates", Tags.of("symbol", symbol)).increment();
            } else {
                log.warn("Out of sync depth update for {}, requesting snapshot", symbol);
                orderBookManager.requestSnapshot(symbol);
            }
        } catch (Exception e) {
            log.error("Error parsing depth update", e);
            meterRegistry.counter("parser.depth.error", Tags.of("symbol", symbol)).increment();
        }
    }

    private void applyDepthUpdate(OrderBook orderBook, JsonNode node) {
        JsonNode bidsNode = node.get("b");
        if (bidsNode != null && bidsNode.isArray()) {
            for (JsonNode bid : bidsNode) {
                BigDecimal price = new BigDecimal(bid.get(0).asText());
                BigDecimal quantity = new BigDecimal(bid.get(1).asText());

                if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                    orderBook.getBids().remove(price);
                } else {
                    orderBook.getBids().put(price, new OrderBookEntry(price, quantity));
                }
            }
        }

        JsonNode asksNode = node.get("a");
        if (asksNode != null && asksNode.isArray()) {
            for (JsonNode ask : asksNode) {
                BigDecimal price = new BigDecimal(ask.get(0).asText());
                BigDecimal quantity = new BigDecimal(ask.get(1).asText());

                if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                    orderBook.getAsks().remove(price);
                } else {
                    orderBook.getAsks().put(price, new OrderBookEntry(price, quantity));
                }
            }
        }
    }

    private void handleTickerEvent(JsonNode node, String symbol, long eventTime, long sequence) {
        try {
            Ticker ticker = Ticker.builder()
                .symbol(symbol)
                .lastPrice(new BigDecimal(node.get("c").asText()))
                .priceChange(new BigDecimal(node.get("p").asText()))
                .priceChangePercent(new BigDecimal(node.get("P").asText()))
                .highPrice(new BigDecimal(node.get("h").asText()))
                .lowPrice(new BigDecimal(node.get("l").asText()))
                .volume(new BigDecimal(node.get("v").asText()))
                .quoteAssetVolume(new BigDecimal(node.get("q").asText()))
                .timestamp(Instant.ofEpochMilli(eventTime))
                .receivedAt(System.currentTimeMillis())
                .build();

            TickerEvent event = new TickerEvent(
                symbol,
                Instant.ofEpochMilli(eventTime),
                sequence,
                ticker
            );

            eventBus.publish(event);
            meterRegistry.counter("tickers.received", Tags.of("symbol", symbol)).increment();
        } catch (Exception e) {
            log.error("Error parsing ticker event", e);
            meterRegistry.counter("parser.ticker.error", Tags.of("symbol", symbol)).increment();
        }
    }
}
