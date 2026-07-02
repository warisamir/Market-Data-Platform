package com.market.data.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeResponse {
    private String symbol;
    private BigDecimal price;
    private BigDecimal quantity;
    private Instant timestamp;
    private long exchangeTradeId;
    private boolean buyerIsMaker;
    private long latencyMs;
}
