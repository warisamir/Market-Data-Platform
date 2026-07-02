package com.market.data.event;

import com.market.data.domain.event.MarketEvent;
import java.util.concurrent.BlockingQueue;

public interface EventBus {
    void publish(MarketEvent event);

    boolean subscribe(String symbol, EventConsumer consumer);

    boolean unsubscribe(String symbol, EventConsumer consumer);

    int getSubscriberCount(String symbol);

    int getQueueSize();

    void start();

    void shutdown();

    boolean isRunning();

    BlockingQueue<?> getInternalQueue();
}
