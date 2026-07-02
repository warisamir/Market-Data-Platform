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
public class Trade {
    private String symbol;
    private BigDecimal price;
    private BigDecimal quantity;
    private Instant timestamp;
    private long exchangeTradeId;
    private String buyerOrderId;
    private String sellerOrderId;
    private boolean buyerIsMaker;
    private long receivedAt;

    public long latencyMs() {
        return (System.currentTimeMillis() - receivedAt);
    }
}
