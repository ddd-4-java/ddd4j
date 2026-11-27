/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.data.projection.micronaut;

import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** \u901a\u8fc7 <code>scheduler.schedule()</code>\u3001<code>handle.isActive()</code>\u3001<code>handle.cancel()</code> \u9a8c\u8bc1 <code>MicronautProjectionSchedulerRace</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class MicronautProjectionSchedulerRaceTest {

    /** \u9a8c\u8bc1<code>handle.isActive()</code>\u4e3a\u771f\uff0c<code>handle.isActive()</code>\u4e3a\u5047\uff0c<code>executor.pending.isCancelled()</code>\u4e3a\u771f\u3002 */
    @Test
    void completedInitialSubmissionCannotReplaceRescheduledFuture() {
        ImmediateFirstExecutor executor = new ImmediateFirstExecutor();
        MicronautProjectionScheduler scheduler = new MicronautProjectionScheduler(executor);
        try {
            ViewScheduler.ViewScheduleHandle handle = scheduler.schedule(
                    "quick-view", "0/1 * * * * *", () -> {
                    });

            assertTrue(handle.isActive());
            handle.cancel();
            assertFalse(handle.isActive());
            assertTrue(executor.pending.isCancelled());
        } finally {
            scheduler.shutdown();
        }
    }

    /** \u5b9e\u73b0 <code>ScheduledThreadPoolExecutor</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>schedule()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class ImmediateFirstExecutor extends ScheduledThreadPoolExecutor {
        /** \u6536\u96c6\u8c03\u5ea6\u5668\u6216\u6267\u884c\u5668\u5b9e\u9645\u6536\u5230\u7684\u4efb\u52a1\u3002 */
        private int submissions;
        /** \u4fdd\u5b58\u5f53\u524d\u5c1a\u672a\u6267\u884c\u6216\u5c1a\u672a\u5904\u7406\u5b8c\u6210\u7684\u6d4b\u8bd5\u64cd\u4f5c\u3002 */
        private final StubFuture pending = new StubFuture();

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>ImmediateFirstExecutor</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        private ImmediateFirstExecutor() {
            super(1);
        }

        /**
         * \u6309 <code>(submissions++ == 0)</code> \u7b49\u5b9e\u9645\u6761\u4ef6\u9009\u62e9\u5f53\u524d\u7528\u4f8b\u8fd4\u56de\u7684\u7ed3\u679c\u3002
         * @param command \u4ea4\u7ed9\u603b\u7ebf\u5904\u7406\u7684\u6d4b\u8bd5\u547d\u4ee4
         * @param delay \u6a21\u62df\u7b49\u5f85\u6240\u7528\u7684\u5ef6\u8fdf
         * @param unit \u65f6\u957f\u53c2\u6570\u6240\u4f7f\u7528\u7684\u65f6\u95f4\u5355\u4f4d
         * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>completed</code>\u3001<code>pending</code>
         */
        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            if (submissions++ == 0) {
                command.run();
                StubFuture completed = new StubFuture();
                completed.complete();
                return completed;
            }
            return pending;
        }
    }

    /** \u5b9e\u73b0 <code>ScheduledFuture</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>complete()</code>\u3001<code>getDelay()</code>\u3001<code>compareTo()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class StubFuture implements ScheduledFuture<Object> {
        /** \u4f9b\u6d4b\u8bd5\u66ff\u8eab\u8f6c\u53d1\u5b9e\u9645\u884c\u4e3a\u7684\u5e95\u5c42\u5b9e\u73b0\u3002 */
        private final CompletableFuture<Object> delegate = new CompletableFuture<>();

        /** \u8c03\u7528 <code>delegate.complete()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
        private void complete() {
            delegate.complete(null);
        }

        /**
         * \u8bfb\u53d6 <code>0L</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param unit \u65f6\u957f\u53c2\u6570\u6240\u4f7f\u7528\u7684\u65f6\u95f4\u5355\u4f4d
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>0L</code>
         */
        @Override
        public long getDelay(TimeUnit unit) {
            return 0L;
        }

        /**
         * \u8bfb\u53d6 <code>0</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param other \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>Delayed</code> \u53c2\u6570 <code>other</code>
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>0</code>
         */
        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        /**
         * \u901a\u8fc7 <code>delegate.cancel()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param mayInterruptIfRunning \u4f20\u7ed9 <code>delegate.cancel()</code> \u7684 <code>boolean</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.cancel(mayInterruptIfRunning)</code>
         */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return delegate.cancel(mayInterruptIfRunning);
        }

        /**
         * \u901a\u8fc7 <code>delegate.isCancelled()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.isCancelled()</code>
         */
        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        /**
         * \u901a\u8fc7 <code>delegate.isDone()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.isDone()</code>
         */
        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        /**
         * \u901a\u8fc7 <code>delegate.get()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.get()</code>
         * @throws InterruptedException \u7b49\u5f85\u6d4b\u8bd5\u64cd\u4f5c\u65f6\u88ab\u4e2d\u65ad
         * @throws ExecutionException \u5f02\u6b65\u6d4b\u8bd5\u64cd\u4f5c\u5931\u8d25
         */
        @Override
        public Object get() throws InterruptedException, ExecutionException {
            return delegate.get();
        }

        /**
         * \u901a\u8fc7 <code>delegate.get()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param timeout \u7b49\u5f85\u6d4b\u8bd5\u4e8b\u4ef6\u7684\u8d85\u65f6\u65f6\u957f
         * @param unit \u65f6\u957f\u53c2\u6570\u6240\u4f7f\u7528\u7684\u65f6\u95f4\u5355\u4f4d
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>delegate.get(timeout, unit)</code>
         * @throws InterruptedException \u7b49\u5f85\u6d4b\u8bd5\u64cd\u4f5c\u65f6\u88ab\u4e2d\u65ad
         * @throws ExecutionException \u5f02\u6b65\u6d4b\u8bd5\u64cd\u4f5c\u5931\u8d25
         * @throws TimeoutException \u7b49\u5f85\u6d4b\u8bd5\u64cd\u4f5c\u7ed3\u679c\u8d85\u65f6
         */
        @Override
        public Object get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }
    }
}
