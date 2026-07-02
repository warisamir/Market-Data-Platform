package com.market.data.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderBookEntry {
    private BigDecimal price;
    private BigDecimal quantity;

    public boolean isEmpty() {
        return quantity.compareTo(BigDecimal.ZERO) <= 0;
    }
}
