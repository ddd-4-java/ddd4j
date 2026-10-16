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

class MicronautProjectionSchedulerRaceTest {

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

    private static final class ImmediateFirstExecutor extends ScheduledThreadPoolExecutor {
        private int submissions;
        private final StubFuture pending = new StubFuture();

        private ImmediateFirstExecutor() {
            super(1);
        }

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

    private static final class StubFuture implements ScheduledFuture<Object> {
        private final CompletableFuture<Object> delegate = new CompletableFuture<>();

        private void complete() {
            delegate.complete(null);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return 0L;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            return delegate.get();
        }

        @Override
        public Object get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }
    }
}
