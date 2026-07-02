package com.market.data.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderBookResponse {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Level {
        private BigDecimal price;
        private BigDecimal quantity;
    }

    private String symbol;
    private long updateId;
    private List<Level> bids;
    private List<Level> asks;
    private Instant timestamp;
    private long depth;
    private BigDecimal spread;
    private BigDecimal midPrice;
}
