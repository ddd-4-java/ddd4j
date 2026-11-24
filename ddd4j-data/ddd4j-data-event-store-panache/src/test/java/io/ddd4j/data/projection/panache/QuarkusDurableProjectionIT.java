package io.ddd4j.data.projection.panache;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.EventChunk;
import io.ddd4j.core.cqrs.readmodel.EventChunkReader;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.impl.matchers.GroupMatcher;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 原生 ArC + Narayana + Hibernate/Panache：共享执行、历史位置和同事务回滚。 */
@QuarkusTest
@TestProfile(QuarkusDurableProjectionIT.DurableProfile.class)
class QuarkusDurableProjectionIT {
    public static class DurableProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("ddd4j.quarkus.cqrs.projection.mode", "durable");
        }
    }

    @Inject ViewManager manager;
    @Inject QuarkusProjectionPositionRepository positions;
    @Inject ProjectionViewBean view;
    @Inject CommandBus commands;
    @Inject Scheduler scheduler;
    @Inject jakarta.enterprise.event.Event<io.quarkus.runtime.StartupEvent> startups;
    @Inject jakarta.persistence.EntityManager entityManager;

    @Test
    void historicalPositionFailureRollbackRetryAndSingleScheduler() throws Exception {
        manager.stop();
        positions.save(new DefaultProjectionPosition("r08-events", 7L));
        positions.save(new DefaultProjectionPosition("r08-read-model", 7L));
        view.setFail(true);
        assertThatThrownBy(manager::triggerOnce).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("projection failed");
        entityManager.clear();
        assertThat(positions.findByStreamId("r08-events").orElseThrow().getNextEventNumber()).isEqualTo(7L);
        assertThat(positions.findByStreamId("r08-read-model").orElseThrow().getNextEventNumber()).isEqualTo(7L);
        view.setFail(false);
        manager.triggerOnce();
        entityManager.clear();
        assertThat(ProjectionBeans.readFrom.get()).isEqualTo(7L);
        assertThat(positions.findByStreamId("r08-events").orElseThrow().getNextEventNumber()).isEqualTo(11L);
        assertThat(positions.findByStreamId("r08-read-model").orElseThrow().getNextEventNumber()).isEqualTo(11L);
        manager.start();
        manager.start();
        startups.fire(new io.quarkus.runtime.StartupEvent());
        assertThat(scheduler.getJobKeys(GroupMatcher.anyJobGroup())).hasSize(1);
        manager.stop();
        assertThat(scheduler.getJobKeys(GroupMatcher.anyJobGroup())).isEmpty();
        manager.start();
        manager.triggerOnce();
        assertThat(ProjectionBeans.readFrom.get()).isEqualTo(11L);
        manager.stop();
    }

    @Test
    void commandKeepsExecutorOwnedJtaBoundaryAndFailureResult() {
        positions.deleteByStreamId("r08-command");
        assertThatThrownBy(() -> commands.execute(new TxCommand(true))).hasMessageContaining("command failed");
        assertThat(positions.findByStreamId("r08-command")).isEmpty();
        Result<?> result = commands.execute(new TxCommand(false));
        assertThat(result.isSuccess()).isFalse();
        // 旧 Quarkus 总线没有 Result.fail 自动回滚规则；事务归执行器所有。
        assertThat(positions.findByStreamId("r08-command").orElseThrow().getNextEventNumber()).isEqualTo(19L);
    }

    public static class TxCommand implements Command {
        final boolean throwsException;
        public TxCommand(boolean throwsException) { this.throwsException = throwsException; }
    }

    @ApplicationScoped
    @IfBuildProperty(name = "ddd4j.quarkus.cqrs.projection.mode", stringValue = "durable")
    public static class TxExecutor implements CommandExecutor<TxCommand> {
        @Inject QuarkusProjectionPositionRepository positions;
        @Override public Set<Class<? extends Command>> supportedCommands() { return Set.of(TxCommand.class); }
        @Override @Transactional
        public Result<?> execute(TxCommand command) {
            positions.save(new DefaultProjectionPosition("r08-command", 19L));
            if (command.throwsException) { throw new IllegalStateException("command failed"); }
            return Result.fail("business failure");
        }
    }

    @ApplicationScoped
    @IfBuildProperty(name = "ddd4j.quarkus.cqrs.projection.mode", stringValue = "durable")
    public static class ProjectionBeans {
        static final AtomicLong readFrom = new AtomicLong(-1);
        @Produces @Singleton
        EventChunkReader<Object> reader() {
            return (stream, from, size, types) -> {
                readFrom.set(from);
                return from < 11 ? new EventChunk<>(List.of(11L), 11) : EventChunk.empty(from);
            };
        }
    }

    @ApplicationScoped
    @IfBuildProperty(name = "ddd4j.quarkus.cqrs.projection.mode", stringValue = "durable")
    public static class ProjectionViewBean implements ProjectionView<Object> {
        @Inject QuarkusProjectionPositionRepository positions;
        volatile boolean fail;
        public void setFail(boolean value) { fail = value; }
        @Override public String getName() { return "r08-events"; }
        @Override public String getCron() { return "0 0 0 1 JAN ? 2099"; }
        @Override public Collection<String> getEventTypes() { return Set.of("PositionAdvanced"); }
        @Override public void handleEvents(Collection<Object> events) {
            positions.save(new DefaultProjectionPosition("r08-read-model", (Long) events.iterator().next()));
            if (fail) { throw new IllegalStateException("projection failed"); }
        }
    }
}
