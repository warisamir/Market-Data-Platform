package com.market.data.event.impl;

import com.market.data.domain.event.MarketEvent;
import com.market.data.event.EventBus;
import com.market.data.event.EventConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LinkedEventBus implements EventBus {
    private static final int QUEUE_CAPACITY = 100000;
    private static final int WORKER_THREADS = 4;

    private final BlockingQueue<MarketEvent> eventQueue;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<EventConsumer>> subscribers;
    private final ExecutorService workerExecutor;
    private final ExecutorService dispatcherExecutor;
    private final AtomicBoolean running;
    private final MeterRegistry meterRegistry;

    public LinkedEventBus(MeterRegistry meterRegistry) {
        this.eventQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        this.subscribers = new ConcurrentHashMap<>();
        this.workerExecutor = Executors.newFixedThreadPool(WORKER_THREADS, r -> {
            Thread t = new Thread(r, "EventBusWorker-" + System.nanoTime());
            t.setDaemon(false);
            return t;
        });
        this.dispatcherExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "EventBusDispatcher");
            t.setDaemon(false);
            return t;
        });
        this.running = new AtomicBoolean(false);
        this.meterRegistry = meterRegistry;

        initializeMetrics();
    }

    private void initializeMetrics() {
        meterRegistry.gauge("eventbus.queue.size", this.eventQueue::size);
        meterRegistry.gauge("eventbus.subscribers.total", this::getTotalSubscribers);
    }

    @Override
    public void publish(MarketEvent event) {
        if (!running.get()) {
            log.warn("EventBus not running, dropping event: {}", event.getEventId());
            return;
        }

        if (!eventQueue.offer(event)) {
            log.error("Failed to publish event, queue full: {}", event.getEventId());
            meterRegistry.counter("eventbus.publish.failed", Tags.of("reason", "queue_full")).increment();
        }
    }

    @Override
    public boolean subscribe(String symbol, EventConsumer consumer) {
        if (consumer == null || symbol == null) {
            log.warn("Invalid subscription: symbol={}, consumer={}", symbol, consumer);
            return false;
        }

        subscribers.computeIfAbsent(symbol, k -> new CopyOnWriteArrayList<>())
            .addIfAbsent(consumer);

        log.info("Subscribed to {}: {}", symbol, consumer.getConsumerId());
        meterRegistry.counter("eventbus.subscriptions", Tags.of("symbol", symbol, "action", "subscribe")).increment();
        return true;
    }

    @Override
    public boolean unsubscribe(String symbol, EventConsumer consumer) {
        CopyOnWriteArrayList<EventConsumer> consumerList = subscribers.get(symbol);
        if (consumerList == null) {
            return false;
        }

        boolean removed = consumerList.remove(consumer);
        if (consumerList.isEmpty()) {
            subscribers.remove(symbol);
        }

        log.info("Unsubscribed from {}: {}", symbol, consumer.getConsumerId());
        meterRegistry.counter("eventbus.subscriptions", Tags.of("symbol", symbol, "action", "unsubscribe")).increment();
        return removed;
    }

    @Override
    public int getSubscriberCount(String symbol) {
        CopyOnWriteArrayList<EventConsumer> consumers = subscribers.get(symbol);
        return consumers == null ? 0 : consumers.size();
    }

    private int getTotalSubscribers() {
        return subscribers.values().stream().mapToInt(List::size).sum();
    }

    @Override
    public int getQueueSize() {
        return eventQueue.size();
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting EventBus with {} worker threads", WORKER_THREADS);

            for (int i = 0; i < WORKER_THREADS; i++) {
                workerExecutor.submit(this::processEvents);
            }

            log.info("EventBus started successfully");
        }
    }

    @Override
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            log.info("Shutting down EventBus");

            dispatcherExecutor.shutdownNow();
            workerExecutor.shutdownNow();

            try {
                if (!workerExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.warn("Worker executor did not terminate gracefully");
                }
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for worker executor shutdown", e);
                Thread.currentThread().interrupt();
            }

            eventQueue.clear();
            log.info("EventBus shut down");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public BlockingQueue<?> getInternalQueue() {
        return eventQueue;
    }

    private void processEvents() {
        log.debug("Event processing worker started: {}", Thread.currentThread().getName());

        while (running.get()) {
            try {
                MarketEvent event = eventQueue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                if (event == null) {
                    continue;
                }

                dispatchEvent(event);
            } catch (InterruptedException e) {
                log.debug("Event processor interrupted", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error processing event", e);
                meterRegistry.counter("eventbus.process.error").increment();
            }
        }

        log.debug("Event processing worker stopped: {}", Thread.currentThread().getName());
    }

    private void dispatchEvent(MarketEvent event) {
        String symbol = event.getSymbol();
        CopyOnWriteArrayList<EventConsumer> consumerList = subscribers.get(symbol);

        if (consumerList == null || consumerList.isEmpty()) {
            log.debug("No subscribers for symbol: {}", symbol);
            return;
        }

        long startTime = System.nanoTime();

        for (EventConsumer consumer : consumerList) {
            try {
                consumer.onEvent(event);
            } catch (Exception e) {
                log.error("Error dispatching event to consumer: {} for event: {}",
                    consumer.getConsumerId(), event.getEventId(), e);
                meterRegistry.counter("eventbus.dispatch.error",
                    Tags.of("consumer", consumer.getConsumerId())).increment();
            }
        }

        long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
        meterRegistry.timer("eventbus.dispatch.latency", Tags.of("symbol", symbol))
            .record(System.nanoTime() - startTime, java.util.concurrent.TimeUnit.NANOSECONDS);

        if (latencyMs > 1000) {
            log.warn("Slow event dispatch for symbol {}: {}ms", symbol, latencyMs);
        }
    }
}
