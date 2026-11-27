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

/** \u9a8c\u8bc1 <code>QuartzProjectionOwnership</code> \u5728 <code>removesCallback</code>\u3001<code>stopsCallbackAndRetainsHandleForRetry</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class QuartzProjectionOwnershipTest {

    /** \u5f53\u524d\u6295\u5f71\u6d4b\u8bd5\u4f7f\u7528\u7684\u8c03\u5ea6\u8868\u8fbe\u5f0f\u3002 */
    private static final String CRON = "0 0 0 1 1 ?";

    /**
     * \u8c03\u7528 <code>Proxy.newProxyInstance()</code>\u3001<code>Scheduler.class.getClassLoader()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param calls \u4f20\u7ed9 <code>Proxy.newProxyInstance()</code> \u7684 <code>InvocationHandler</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>(Scheduler)Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class&lt;?&gt;[]{Scheduler.class}, calls)</code>
     */
    private static Scheduler scheduler(InvocationHandler calls) {
        return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(),
                new Class<?>[]{Scheduler.class}, calls);
    }

    /** \u9a8c\u8bc1<code>RunnableHolder.get(identity.get())</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>callback</code>\uff0c<code>identity.get()</code>\u975e\u7a7a\uff0c<code>RunnableHolder.get(identity.get())</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>RunnableHolder.get(identity.get())</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>method.getName()</code> \u7b49\u4e8e <code>"deleteJob"</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>deleteAttempts.get()</code> \u7b49\u4e8e <code>2</code>\u3002 */
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
