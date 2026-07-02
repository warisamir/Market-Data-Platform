package com.market.data.domain;

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
public class Ticker {
    private String symbol;
    private BigDecimal lastPrice;
    private BigDecimal priceChange;
    private BigDecimal priceChangePercent;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal volume;
    private BigDecimal quoteAssetVolume;
    private Instant timestamp;
    private long receivedAt;

    public long latencyMs() {
        return System.currentTimeMillis() - receivedAt;
    }
}
