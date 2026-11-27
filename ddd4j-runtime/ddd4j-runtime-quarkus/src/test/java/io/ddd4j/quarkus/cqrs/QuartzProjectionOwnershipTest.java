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

class QuartzProjectionOwnershipTest {

    private static final String CRON = "0 0 0 1 1 ?";

    private static Scheduler scheduler(InvocationHandler calls) {
        return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(),
                new Class<?>[]{Scheduler.class}, calls);
    }

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
