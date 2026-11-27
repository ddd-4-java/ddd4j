/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.micronaut.cqrs;

import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通过 <code>scheduler.schedule()</code>、<code>handle.isActive()</code>、<code>handle.cancel()</code> 验证 <code>MicronautProjectionSchedulerRace</code> 的运行行为。
 */
class MicronautProjectionSchedulerRaceTest {

    /**
     * 验证<code>handle.isActive()</code>为真，<code>handle.isActive()</code>为假，<code>executor.pending.isCancelled()</code>为真。
     */
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

    /**
     * 实现 <code>ScheduledThreadPoolExecutor</code>，供测试调用 <code>schedule()</code> 并观察结果。
     */
    private static final class ImmediateFirstExecutor extends ScheduledThreadPoolExecutor {
        /**
         * 保存当前尚未执行或尚未处理完成的测试操作。
         */
        private final StubFuture pending = new StubFuture();
        /**
         * 收集调度器或执行器实际收到的任务。
         */
        private int submissions;

        /**
         * 使用 <code>super()</code> 初始化 <code>ImmediateFirstExecutor</code> 测试夹具。
         */
        private ImmediateFirstExecutor() {
            super(1);
        }

        /**
         * 按 <code>(submissions++ == 0)</code> 等实际条件选择当前用例返回的结果。
         *
         * @param command 交给总线处理的测试命令
         * @param delay   模拟等待所用的延迟
         * @param unit    时长参数所使用的时间单位
         * @return 根据测试条件返回的可能值，包括 <code>completed</code>、<code>pending</code>
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

    /**
     * 实现 <code>ScheduledFuture</code>，供测试调用 <code>complete()</code>、<code>getDelay()</code>、<code>compareTo()</code> 并观察结果。
     */
    private static final class StubFuture implements ScheduledFuture<Object> {
        /**
         * 供测试替身转发实际行为的底层实现。
         */
        private final CompletableFuture<Object> delegate = new CompletableFuture<>();

        /**
         * 调用 <code>delegate.complete()</code> 执行当前夹具操作。
         */
        private void complete() {
            delegate.complete(null);
        }

        /**
         * 读取 <code>0L</code> 供当前测试使用。
         *
         * @param unit 时长参数所使用的时间单位
         * @return 当前测试使用的结果，取自 <code>0L</code>
         */
        @Override
        public long getDelay(TimeUnit unit) {
            return 0L;
        }

        /**
         * 读取 <code>0</code> 供当前测试使用。
         *
         * @param other 本次调用使用的 <code>Delayed</code> 参数 <code>other</code>
         * @return 当前测试使用的结果，取自 <code>0</code>
         */
        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        /**
         * 通过 <code>delegate.cancel()</code> 取得当前测试需要的结果。
         *
         * @param mayInterruptIfRunning 传给 <code>delegate.cancel()</code> 的 <code>boolean</code> 输入
         * @return 当前测试使用的结果，取自 <code>delegate.cancel(mayInterruptIfRunning)</code>
         */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return delegate.cancel(mayInterruptIfRunning);
        }

        /**
         * 通过 <code>delegate.isCancelled()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>delegate.isCancelled()</code>
         */
        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        /**
         * 通过 <code>delegate.isDone()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>delegate.isDone()</code>
         */
        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        /**
         * 通过 <code>delegate.get()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>delegate.get()</code>
         * @throws InterruptedException 等待测试操作时被中断
         * @throws ExecutionException   异步测试操作失败
         */
        @Override
        public Object get() throws InterruptedException, ExecutionException {
            return delegate.get();
        }

        /**
         * 通过 <code>delegate.get()</code> 取得当前测试需要的结果。
         *
         * @param timeout 等待测试事件的超时时长
         * @param unit    时长参数所使用的时间单位
         * @return 当前测试使用的结果，取自 <code>delegate.get(timeout, unit)</code>
         * @throws InterruptedException 等待测试操作时被中断
         * @throws ExecutionException   异步测试操作失败
         * @throws TimeoutException     等待测试操作结果超时
         */
        @Override
        public Object get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }
    }
}
