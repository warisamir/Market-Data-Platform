package com.market.data.event;

import com.market.data.domain.event.MarketEvent;

public interface EventConsumer {
    void onEvent(MarketEvent event);

    String getConsumerId();

    String getSymbol();
}
