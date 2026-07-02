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
public class PriceResponse {
    private String symbol;
    private BigDecimal price;
    private BigDecimal bid;
    private BigDecimal ask;
    private BigDecimal spread;
    private BigDecimal midPrice;
    private Instant timestamp;
    private long latencyMs;
}
