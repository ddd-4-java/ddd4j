package io.ddd4j.guice.cqrs;

import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.cqrs.readmodel.ViewScheduler;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Guice 环境默认的 CQRS 读侧视图管理器。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class GuiceViewManager implements ViewManager, ViewScheduler, AutoCloseable {

    /**
     * 运行状态标志
     */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /**
     * 已调度的视图任务句柄集
     */
    private final ConcurrentMap<String, ScheduledFuture<?>> handles = new ConcurrentHashMap<>();
    /**
     * 调度线程池执行器
     */
    private ScheduledExecutorService executor;

    /**
     * 启动当前组件并准备其运行资源。
     *
     * <p>此实现调用 <code>running.compareAndSet(false, true)</code>。</p>
     */
    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            executor = Executors.newScheduledThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "ddd4j-runtime-guice-view-manager");
                thread.setDaemon(true);
                return thread;
            });
            log.info("GuiceViewManager started");
        }
    }

    /**
     * 停止当前组件并清理已登记的运行资源。
     *
     * <p>此实现调用 <code>running.compareAndSet(true, false)</code>。</p>
     */
    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (Objects.nonNull(executor)) {
                executor.shutdownNow();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.warn("ViewManager executor did not terminate in time");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                executor = null;
            }
            handles.clear();
            log.info("GuiceViewManager stopped");
        }
    }

    /**
     * 结束当前组件持有的资源。
     *
     * <p>此实现调用 <code>stop()</code>。</p>
     */
    @Override
    public void close() {
        stop();
    }

    /**
     * 检查当前组件是否仍处于运行状态。
     *
     * <p>此实现调用 <code>running.get()</code>。</p>
     *
     * @return 实际返回<code>running.get()</code>
     */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 请求立即执行指定投影视图的一轮更新。
     *
     * <p>此实现调用 <code>log.info("triggerOnce() should be implemented by concrete view logic")</code>。</p>
     */
    @Override
    public void triggerOnce() {
        log.info("triggerOnce() should be implemented by concrete view logic");
    }

    /**
     * 按给定时间规则安排任务。
     *
     * <p>此实现调用 <code>ensureStarted()</code>。</p>
     *
     * @param viewName 视图名称（用于日志 / 调试）
     * @param cron CRON 表达式
     * @param task 要执行的任务
     * @return 任务句柄，可用于取消
     */
    @Override
    public ViewScheduleHandle schedule(String viewName, String cron, Runnable task) {
        ensureStarted();
        long period = parseCronToPeriodSeconds(cron);
        java.util.concurrent.ScheduledFuture<?> future =
                executor.scheduleAtFixedRate(task, period, period, TimeUnit.SECONDS);
        handles.put(viewName, future);
        log.info("View scheduled: {} period={}s", viewName, period);
        return new GuiceViewScheduleHandle(future);
    }

    /** \u8981\u6c42\u5f53\u524d\u89c6\u56fe\u7ba1\u7406\u5668\u5df2\u5b8c\u6210\u542f\u52a8\u3002 */
    private void ensureStarted() {
        if (!isRunning()) {
            start();
        }
    }

    /**
     * \u4ece\u517c\u5bb9 Cron \u914d\u7f6e\u8f6c\u6362\u4e3a\u5b9a\u65f6\u5668\u6240\u7528\u7684\u79d2\u6570\u3002
     * @param cron \u4efb\u52a1\u8ba1\u5212\u7684 Cron \u8868\u8fbe\u5f0f
     * @return \u517c\u5bb9\u8c03\u5ea6\u4f7f\u7528\u7684\u5468\u671f\u79d2\u6570
     */
    private long parseCronToPeriodSeconds(String cron) {
        if (StrKit.isEmpty(cron)) {
            return 60L;
        }
        if (cron.startsWith("0/")) {
            try {
                return Long.parseLong(cron.substring(2).split("\\s+")[0]);
            } catch (NumberFormatException exception) {
                return 60L;
            }
        }
        return 60L;
    }

    /** \u4fdd\u5b58 Guice \u6295\u5f71\u4efb\u52a1\u7684\u5b9a\u65f6 Future\uff0c\u5e76\u63d0\u4f9b\u53d6\u6d88\u5165\u53e3\u3002 */
    private static class GuiceViewScheduleHandle implements ViewScheduleHandle {

        /** 下一次投影任务对应的定时执行句柄。 */
        private final java.util.concurrent.ScheduledFuture<?> future;

        /**
         * 初始化 <code>GuiceViewScheduleHandle</code> 使用的 <code>future</code>。
         *
         * @param future \u7528\u4e8e\u8ddf\u8e2a\u548c\u53d6\u6d88\u540e\u53f0\u8c03\u5ea6\u4efb\u52a1\u7684\u53e5\u67c4
         */
        GuiceViewScheduleHandle(java.util.concurrent.ScheduledFuture<?> future) {
            this.future = future;
        }

        /**
         * 撤销当前任务句柄允许撤销的操作。
         *
         * <p>此实现调用 <code>future.cancel(false)</code>。</p>
         */
        @Override
        public void cancel() {
            future.cancel(false);
        }

        /**
         * 检查当前句柄是否仍处于有效状态。
         *
         * <p>此实现调用 <code>future.isCancelled()</code>。</p>
         *
         * @return 实际返回<code>!future.isCancelled() &amp;&amp; !future.isDone()</code>
         */
        @Override
        public boolean isActive() {
            return !future.isCancelled() && !future.isDone();
        }
    }
}
