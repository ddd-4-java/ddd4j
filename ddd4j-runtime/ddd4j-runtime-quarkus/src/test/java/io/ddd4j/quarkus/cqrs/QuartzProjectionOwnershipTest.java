package io.ddd4j.quarkus.cqrs;

import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 <code>QuartzProjectionOwnership</code> 在 <code>removesCallback</code>、<code>stopsCallbackAndRetainsHandleForRetry</code> 等场景中的行为。
 */
class QuartzProjectionOwnershipTest {

    /**
     * 当前投影测试使用的调度表达式。
     */
    private static final String CRON = "0 0 0 1 1 ?";

    /**
     * 调用 <code>Proxy.newProxyInstance()</code>、<code>Scheduler.class.getClassLoader()</code> 执行当前夹具操作。
     *
     * @param calls 传给 <code>Proxy.newProxyInstance()</code> 的 <code>InvocationHandler</code> 输入
     * @return 当前测试使用的结果，取自 <code>(Scheduler)Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class&lt;?&gt;[]{Scheduler.class}, calls)</code>
     */
    private static Scheduler scheduler(InvocationHandler calls) {
        return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(),
                new Class<?>[]{Scheduler.class}, calls);
    }

    /**
     * 验证<code>RunnableHolder.get(identity.get())</code> 指向同一实例 <code>callback</code>，<code>identity.get()</code>非空，<code>RunnableHolder.get(identity.get())</code>为空。
     */
    @Test
    void removesCallbackWhenSchedulerRejectsSubmissionWithUncheckedFailure() {
        RuntimeException rejection = new IllegalStateException("scheduler rejected");
        AtomicReference<String> identity = new AtomicReference<>();
        Runnable callback = () -> {
        };
        Scheduler scheduler = scheduler((proxy, method, arguments) -> {
            JobDetail job = (JobDetail) arguments[0];
            identity.set(job.getJobDataMap().getString(QuarkusViewScheduler.TASK_KEY));
            assertThat(RunnableHolder.get(identity.get())).isSameAs(callback);
            throw rejection;
        });

        try {
            assertThatThrownBy(() -> QuartzViewScheduleHandle.schedule(scheduler, "test", CRON, callback))
                    .isSameAs(rejection);
            assertThat(identity.get()).isNotNull();
            assertThat(RunnableHolder.get(identity.get())).isNull();
        } finally {
            if (Objects.nonNull(identity.get())) {
                RunnableHolder.remove(identity.get());
            }
        }
    }

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>，<code>RunnableHolder.get(identity.get())</code>为空。
     */
    @Test
    void wrapsCheckedSubmissionFailureAndRemovesCallback() {
        SchedulerException rejection = new SchedulerException("scheduler rejected");
        AtomicReference<String> identity = new AtomicReference<>();
        Scheduler scheduler = scheduler((proxy, method, arguments) -> {
            JobDetail job = (JobDetail) arguments[0];
            identity.set(job.getJobDataMap().getString(QuarkusViewScheduler.TASK_KEY));
            throw rejection;
        });

        assertThatThrownBy(() -> QuartzViewScheduleHandle.schedule(scheduler, "test", CRON, () -> {
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to schedule view 'test'")
                .hasCause(rejection);
        assertThat(RunnableHolder.get(identity.get())).isNull();
    }

    /**
     * 验证<code>method.getName()</code> 等于 <code>"deleteJob"</code>，被测调用 抛出 <code>IllegalStateException.class</code>，<code>deleteAttempts.get()</code> 等于 <code>2</code>。
     */
    @Test
    void stopsCallbackAndRetainsHandleForRetryWhenSchedulerCannotDeleteJob() {
        SchedulerException deletionFailure = new SchedulerException("deletion failed");
        AtomicInteger deleteAttempts = new AtomicInteger();
        String identity = "failed-delete-test";
        AtomicReference<QuartzViewScheduleHandle> scheduledHandle = new AtomicReference<>();
        Scheduler scheduler = scheduler((proxy, method, arguments) -> {
            assertThat(method.getName()).isEqualTo("deleteJob");
            int attempt = deleteAttempts.incrementAndGet();
            if (attempt == 1) {
                throw deletionFailure;
            }
            if (attempt == 2) {
                scheduledHandle.get().cancel();
            }
            return true;
        });
        QuartzViewScheduleHandle handle = new QuartzViewScheduleHandle(scheduler, identity);
        scheduledHandle.set(handle);
        RunnableHolder.put(identity, () -> {
        });

        try {
            assertThatThrownBy(handle::cancel).isInstanceOf(IllegalStateException.class)
                    .hasCause(deletionFailure);
            assertThat(handle.isActive()).isTrue();
            assertThat(RunnableHolder.get(identity)).isNull();

            handle.cancel();
            handle.cancel();
            assertThat(handle.isActive()).isFalse();
            assertThat(deleteAttempts.get()).isEqualTo(2);
        } finally {
            RunnableHolder.remove(identity);
        }
    }
}
