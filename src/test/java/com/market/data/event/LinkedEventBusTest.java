package com.market.data.event;

import com.market.data.domain.event.MarketEvent;
import com.market.data.domain.event.TradeEvent;
import com.market.data.event.impl.LinkedEventBus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LinkedEventBusTest {
    private LinkedEventBus eventBus;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        eventBus = new LinkedEventBus(meterRegistry);
    }

    @Test
    void testEventBusStartStop() {
        assertFalse(eventBus.isRunning());

        eventBus.start();
        assertTrue(eventBus.isRunning());

        eventBus.shutdown();
        assertFalse(eventBus.isRunning());
    }

    @Test
    void testPublishEvent() {
        eventBus.start();

        TradeEvent event = new TradeEvent("BTCUSDT", Instant.now(), 1, null);
        eventBus.publish(event);

        assertTrue(eventBus.getQueueSize() > 0);

        eventBus.shutdown();
    }

    @Test
    void testSubscribeAndConsume() throws InterruptedException {
        eventBus.start();

        CountDownLatch latch = new CountDownLatch(1);
        List<MarketEvent> receivedEvents = new ArrayList<>();

        EventConsumer consumer = new EventConsumer() {
            @Override
            public void onEvent(MarketEvent event) {
                receivedEvents.add(event);
                latch.countDown();
            }

            @Override
            public String getConsumerId() {
                return "test-consumer";
            }

            @Override
            public String getSymbol() {
                return "BTCUSDT";
            }
        };

        eventBus.subscribe("BTCUSDT", consumer);
        assertEquals(1, eventBus.getSubscriberCount("BTCUSDT"));

        TradeEvent event = new TradeEvent("BTCUSDT", Instant.now(), 1, null);
        eventBus.publish(event);

        // Wait for event to be processed
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Event not consumed within timeout");
        assertEquals(1, receivedEvents.size());

        eventBus.shutdown();
    }

    @Test
    void testMultipleSubscribers() throws InterruptedException {
        eventBus.start();

        CountDownLatch latch = new CountDownLatch(2);
        List<MarketEvent> events1 = new ArrayList<>();
        List<MarketEvent> events2 = new ArrayList<>();

        EventConsumer consumer1 = new EventConsumer() {
            @Override
            public void onEvent(MarketEvent event) {
                events1.add(event);
                latch.countDown();
            }

            @Override
            public String getConsumerId() {
                return "consumer1";
            }

            @Override
            public String getSymbol() {
                return "BTCUSDT";
            }
        };

        EventConsumer consumer2 = new EventConsumer() {
            @Override
            public void onEvent(MarketEvent event) {
                events2.add(event);
                latch.countDown();
            }

            @Override
            public String getConsumerId() {
                return "consumer2";
            }

            @Override
            public String getSymbol() {
                return "BTCUSDT";
            }
        };

        eventBus.subscribe("BTCUSDT", consumer1);
        eventBus.subscribe("BTCUSDT", consumer2);
        assertEquals(2, eventBus.getSubscriberCount("BTCUSDT"));

        TradeEvent event = new TradeEvent("BTCUSDT", Instant.now(), 1, null);
        eventBus.publish(event);

        // Wait for both consumers to receive event
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Not all events consumed within timeout");
        assertEquals(1, events1.size());
        assertEquals(1, events2.size());

        eventBus.shutdown();
    }

    @Test
    void testUnsubscribe() {
        eventBus.start();

        EventConsumer consumer = new EventConsumer() {
            @Override
            public void onEvent(MarketEvent event) {}

            @Override
            public String getConsumerId() {
                return "test-consumer";
            }

            @Override
            public String getSymbol() {
                return "BTCUSDT";
            }
        };

        eventBus.subscribe("BTCUSDT", consumer);
        assertEquals(1, eventBus.getSubscriberCount("BTCUSDT"));

        eventBus.unsubscribe("BTCUSDT", consumer);
        assertEquals(0, eventBus.getSubscriberCount("BTCUSDT"));

        eventBus.shutdown();
    }

    @Test
    void testDifferentSymbolsIndependent() throws InterruptedException {
        eventBus.start();

        CountDownLatch btcLatch = new CountDownLatch(1);
        CountDownLatch ethLatch = new CountDownLatch(1);

        EventConsumer btcConsumer = new EventConsumer() {
            @Override
            public void onEvent(MarketEvent event) {
                btcLatch.countDown();
            }

            @Override
            public String getConsumerId() {
                return "btc-consumer";
            }

            @Override
            public String getSymbol() {
                return "BTCUSDT";
            }
        };

        EventConsumer ethConsumer = new EventConsumer() {
            @Override
            public void onEvent(MarketEvent event) {
                ethLatch.countDown();
            }

            @Override
            public String getConsumerId() {
                return "eth-consumer";
            }

            @Override
            public String getSymbol() {
                return "ETHUSDT";
            }
        };

        eventBus.subscribe("BTCUSDT", btcConsumer);
        eventBus.subscribe("ETHUSDT", ethConsumer);

        // Publish BTC event only
        TradeEvent btcEvent = new TradeEvent("BTCUSDT", Instant.now(), 1, null);
        eventBus.publish(btcEvent);

        // BTC should receive, ETH should not
        assertTrue(btcLatch.await(5, TimeUnit.SECONDS));
        assertFalse(ethLatch.await(1, TimeUnit.SECONDS));

        eventBus.shutdown();
    }
}
