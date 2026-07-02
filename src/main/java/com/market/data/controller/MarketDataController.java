package com.market.data.controller;

import com.market.data.domain.OrderBook;
import com.market.data.domain.Ticker;
import com.market.data.domain.Trade;
import com.market.data.dto.OrderBookResponse;
import com.market.data.dto.PriceResponse;
import com.market.data.dto.TradeResponse;
import com.market.data.service.TradeService;
import com.market.data.websocket.OrderBookManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
@Slf4j
public class MarketDataController {
    private final TradeService tradeService;
    private final OrderBookManager orderBookManager;

    public MarketDataController(TradeService tradeService, OrderBookManager orderBookManager) {
        this.tradeService = tradeService;
        this.orderBookManager = orderBookManager;
    }

    @GetMapping("/prices")
    public ResponseEntity<List<PriceResponse>> getPrices() {
        List<PriceResponse> prices = orderBookManager.getAllOrderBooks().stream()
            .map(this::buildPriceResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(prices);
    }

    @GetMapping("/prices/{symbol}")
    public ResponseEntity<PriceResponse> getPrice(@PathVariable String symbol) {
        OrderBook orderBook = orderBookManager.getOrderBook(symbol.toUpperCase());
        if (orderBook == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(buildPriceResponse(orderBook));
    }

    @GetMapping("/orderbook/{symbol}")
    public ResponseEntity<OrderBookResponse> getOrderBook(
        @PathVariable String symbol,
        @RequestParam(defaultValue = "10") int depth) {

        OrderBook orderBook = orderBookManager.getOrderBook(symbol.toUpperCase());
        if (orderBook == null) {
            return ResponseEntity.notFound().build();
        }

        OrderBookResponse response = buildOrderBookResponse(orderBook, depth);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/trades/{symbol}")
    public ResponseEntity<List<TradeResponse>> getTrades(
        @PathVariable String symbol,
        @RequestParam(defaultValue = "100") int limit) {

        List<Trade> trades = tradeService.getRecentTrades(symbol.toUpperCase(), limit);
        List<TradeResponse> responses = trades.stream()
            .map(this::buildTradeResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/trades/{symbol}/latest")
    public ResponseEntity<TradeResponse> getLatestTrade(@PathVariable String symbol) {
        Trade trade = tradeService.getLatestTrade(symbol.toUpperCase());
        if (trade == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(buildTradeResponse(trade));
    }

    private PriceResponse buildPriceResponse(OrderBook orderBook) {
        BigDecimal lastPrice = null;
        Trade latestTrade = tradeService.getLatestTrade(orderBook.getSymbol());
        if (latestTrade != null) {
            lastPrice = latestTrade.getPrice();
        }

        BigDecimal bid = orderBook.getBids().isEmpty() ? null : orderBook.getBids().firstKey();
        BigDecimal ask = orderBook.getAsks().isEmpty() ? null : orderBook.getAsks().firstKey();

        return PriceResponse.builder()
            .symbol(orderBook.getSymbol())
            .price(lastPrice)
            .bid(bid)
            .ask(ask)
            .spread(orderBook.getSpread())
            .midPrice(orderBook.getMidPrice())
            .timestamp(orderBook.getSnapshotTime())
            .latencyMs(0)
            .build();
    }

    private OrderBookResponse buildOrderBookResponse(OrderBook orderBook, int depth) {
        List<OrderBookResponse.Level> bids = orderBook.getBids().entrySet().stream()
            .limit(depth)
            .map(e -> new OrderBookResponse.Level(e.getKey(), e.getValue().getQuantity()))
            .collect(Collectors.toList());

        List<OrderBookResponse.Level> asks = orderBook.getAsks().entrySet().stream()
            .limit(depth)
            .map(e -> new OrderBookResponse.Level(e.getKey(), e.getValue().getQuantity()))
            .collect(Collectors.toList());

        return OrderBookResponse.builder()
            .symbol(orderBook.getSymbol())
            .updateId(orderBook.getLastUpdateId())
            .bids(bids)
            .asks(asks)
            .timestamp(orderBook.getSnapshotTime())
            .depth(orderBook.getDepth())
            .spread(orderBook.getSpread())
            .midPrice(orderBook.getMidPrice())
            .build();
    }

    private TradeResponse buildTradeResponse(Trade trade) {
        return TradeResponse.builder()
            .symbol(trade.getSymbol())
            .price(trade.getPrice())
            .quantity(trade.getQuantity())
            .timestamp(trade.getTimestamp())
            .exchangeTradeId(trade.getExchangeTradeId())
            .buyerIsMaker(trade.isBuyerIsMaker())
            .latencyMs(trade.latencyMs())
            .build();
    }
}
