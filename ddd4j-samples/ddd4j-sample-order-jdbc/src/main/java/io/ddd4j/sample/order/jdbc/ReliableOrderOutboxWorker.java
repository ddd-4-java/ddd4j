/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.sample.order.jdbc;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 订单 JDBC 样例的可关闭 Outbox 定时工作器。
 *
 * <p>每轮在订单业务事务外调用可靠发布入口；失败只记录并留给下一轮重试，
 * 具体退避和租约仍由公共 Dispatcher 决定。应用负责创建、启动和关闭本工作器。
 */
@Slf4j
public final class ReliableOrderOutboxWorker implements AutoCloseable {

    private final ReliableOrderOutboxPublisher publisher;
    private final int batchSize;
    private final long intervalMillis;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;
    private boolean closed;

    public ReliableOrderOutboxWorker(ReliableOrderOutboxPublisher publisher, int batchSize, Duration interval) {
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        Objects.requireNonNull(interval, "interval must not be null");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (interval.isNegative() || interval.isZero() || interval.toMillis() <= 0) {
            throw new IllegalArgumentException("interval must be at least one millisecond");
        }
        this.batchSize = batchSize;
        this.intervalMillis = interval.toMillis();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ddd4j-order-outbox");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 启动周期发布；重复调用不会创建第二个调度任务。 */
    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("Outbox worker is closed");
        }
        if (Objects.nonNull(scheduledTask)) {
            return;
        }
        scheduledTask = scheduler.scheduleWithFixedDelay(
                this::dispatchOnce, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void dispatchOnce() {
        try {
            publisher.publishPending(batchSize);
        } catch (RuntimeException exception) {
            log.warn("Order outbox dispatch failed; the next scheduled run will retry", exception);
        }
    }

    /** 停止后不允许重新启动；当前正在执行的一轮可能在关闭时才结束。 */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (Objects.nonNull(scheduledTask)) {
            scheduledTask.cancel(true);
        }
        scheduler.shutdownNow();
    }
}
