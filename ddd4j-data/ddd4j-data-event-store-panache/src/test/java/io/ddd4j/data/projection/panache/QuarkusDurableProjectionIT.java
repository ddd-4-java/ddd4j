package io.ddd4j.data.projection.panache;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.core.cqrs.readmodel.*;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
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

/**
 * 原生 ArC + Narayana + Hibernate/Panache：共享执行、历史位置和同事务回滚。
 */
@QuarkusTest
@TestProfile(QuarkusDurableProjectionIT.DurableProfile.class)
class QuarkusDurableProjectionIT {
    /** \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u5e76\u8d1f\u8d23\u91ca\u653e\u7684\u89c6\u56fe\u6216\u8ba4\u8bc1\u7ba1\u7406\u5668\u3002 */
    @Inject
    ViewManager manager;
    /** \u6d4b\u8bd5\u4f7f\u7528\u6216\u8bb0\u5f55\u7684\u591a\u6761\u6295\u5f71\u4f4d\u70b9\u3002 */
    @Inject
    QuarkusProjectionPositionRepository positions;
    /** \u5f53\u524d\u7528\u4f8b\u6ce8\u518c\u548c\u6267\u884c\u7684\u6295\u5f71\u89c6\u56fe\u3002 */
    @Inject
    ProjectionViewBean view;
    /** \u6d4b\u8bd5\u9a71\u52a8\u53d1\u9001\u6216\u8bb0\u5f55\u7684\u5199\u5165\u547d\u4ee4\u3002 */
    @Inject
    CommandBus commands;
    /** \u5f53\u524d\u6295\u5f71\u6d4b\u8bd5\u4f7f\u7528\u7684\u539f\u751f\u8c03\u5ea6\u5668\u6216\u66ff\u8eab\u3002 */
    @Inject
    Scheduler scheduler;
    /** \u7528\u4e8e\u6838\u5bf9\u91cd\u590d\u542f\u52a8\u884c\u4e3a\u7684\u8c03\u7528\u6b21\u6570\u3002 */
    @Inject
    jakarta.enterprise.event.Event<io.quarkus.runtime.StartupEvent> startups;
    /** \u6267\u884c JPA \u67e5\u8be2\u548c\u6301\u4e45\u5316\u64cd\u4f5c\u7684\u5b9e\u4f53\u7ba1\u7406\u5668\u3002 */
    @Inject
    jakarta.persistence.EntityManager entityManager;

    /**
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>ProjectionBeans.readFrom.get()</code> \u7b49\u4e8e <code>11L</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
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

    /** \u9a8c\u8bc1<code>positions.findByStreamId("r08-command")</code>\u4e3a\u7a7a\uff0c<code>result.isSuccess()</code>\u4e3a\u5047\uff0c<code>positions.findByStreamId("r08-command").orElseThrow().getNextEventNumber()</code> \u7b49\u4e8e <code>19L</code>\u3002 */
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

    /** \u5b9e\u73b0 <code>QuarkusTestProfile</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>getConfigOverrides()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    public static class DurableProfile implements QuarkusTestProfile {
        /**
         * \u901a\u8fc7 <code>Map.of()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Map.of("ddd4j.quarkus.cqrs.projection.mode", "durable")</code>
         */
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("ddd4j.quarkus.cqrs.projection.mode", "durable");
        }
    }

    /** \u4f9b\u547d\u4ee4\u603b\u7ebf\u9a8c\u8bc1\u7c7b\u578b\u5339\u914d\u548c\u5206\u53d1\u7684\u547d\u4ee4\u6837\u672c\u3002 */
    public static class TxCommand implements Command {
        /** \u914d\u7f6e\u5f53\u524d\u66ff\u8eab\u8c03\u7528\u65f6\u662f\u5426\u629b\u51fa\u6d4b\u8bd5\u5f02\u5e38\u3002 */
        final boolean throwsException;

        /**
         * \u8bbe\u7f6e <code>throwsException</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>TxCommand</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param throwsException \u7528\u4e8e\u521d\u59cb\u5316 <code>TxCommand</code> \u7684 <code>boolean</code> \u503c
         */
        public TxCommand(boolean throwsException) {
            this.throwsException = throwsException;
        }
    }

    /** \u63a5\u6536\u6d4b\u8bd5\u547d\u4ee4\u5e76\u8fd4\u56de\u53ef\u65ad\u8a00\u7ed3\u679c\u7684\u6267\u884c\u5668\u66ff\u8eab\u3002 */
    @ApplicationScoped
    @IfBuildProperty(name = "ddd4j.quarkus.cqrs.projection.mode", stringValue = "durable")
    public static class TxExecutor implements CommandExecutor<TxCommand> {
        /** \u6d4b\u8bd5\u4f7f\u7528\u6216\u8bb0\u5f55\u7684\u591a\u6761\u6295\u5f71\u4f4d\u70b9\u3002 */
        @Inject
        QuarkusProjectionPositionRepository positions;

        /** {@inheritDoc} */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Set.of(TxCommand.class);
        }

        /** {@inheritDoc} */
        @Override
        @Transactional
        public Result<?> execute(TxCommand command) {
            positions.save(new DefaultProjectionPosition("r08-command", 19L));
            if (command.throwsException) {
                throw new IllegalStateException("command failed");
            }
            return Result.fail("business failure");
        }
    }

    /** \u5411\u6d4b\u8bd5\u5bb9\u5668\u6ce8\u518c <code>reader()</code> \u7b49\u6d4b\u8bd5\u5165\u53e3\u6216\u7ec4\u4ef6\u3002 */
    @ApplicationScoped
    @IfBuildProperty(name = "ddd4j.quarkus.cqrs.projection.mode", stringValue = "durable")
    public static class ProjectionBeans {
        /** \u5728\u6d4b\u8bd5\u7ebf\u7a0b\u4e4b\u95f4\u5171\u4eab\u5f53\u524d\u64cd\u4f5c\u6b21\u6570\u6216\u66f4\u65b0\u6570\u91cf\u7684\u8ba1\u6570\u5668\u3002 */
        static final AtomicLong readFrom = new AtomicLong(-1);

        /**
         * \u8c03\u7528 <code>readFrom.set()</code>\u3001<code>EventChunk.empty()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>from &lt; 11 ? new EventChunk&lt;&gt;(List.of(11L), 11) : EventChunk.empty(from)</code>
         */
        @Produces
        @Singleton
        EventChunkReader<Object> reader() {
            return (stream, from, size, types) -> {
                readFrom.set(from);
                return from < 11 ? new EventChunk<>(List.of(11L), 11) : EventChunk.empty(from);
            };
        }
    }

    /** \u4f9b\u8fd0\u884c\u65f6\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u4f4d\u70b9\u63a8\u8fdb\u7684\u6295\u5f71\u89c6\u56fe\u3002 */
    @ApplicationScoped
    @IfBuildProperty(name = "ddd4j.quarkus.cqrs.projection.mode", stringValue = "durable")
    public static class ProjectionViewBean implements ProjectionView<Object> {
        /** \u6d4b\u8bd5\u4f7f\u7528\u6216\u8bb0\u5f55\u7684\u591a\u6761\u6295\u5f71\u4f4d\u70b9\u3002 */
        @Inject
        QuarkusProjectionPositionRepository positions;
        /** \u4ee4\u5f53\u524d\u66ff\u8eab\u5728\u6307\u5b9a\u6d4b\u8bd5\u8def\u5f84\u4e2d\u8fd4\u56de\u5931\u8d25\u3002 */
        volatile boolean fail;

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>fail = value</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param value \u5f53\u524d\u7528\u4f8b\u4f20\u5165\u7684\u503c
         */
        public void setFail(boolean value) {
            fail = value;
        }

        /** {@inheritDoc} */
        @Override
        public String getName() {
            return "r08-events";
        }

        /** {@inheritDoc} */
        @Override
        public String getCron() {
            return "0 0 0 1 JAN ? 2099";
        }

        /** {@inheritDoc} */
        @Override
        public Collection<String> getEventTypes() {
            return Set.of("PositionAdvanced");
        }

        /** {@inheritDoc} */
        @Override
        public void handleEvents(Collection<Object> events) {
            positions.save(new DefaultProjectionPosition("r08-read-model", (Long) events.iterator().next()));
            if (fail) {
                throw new IllegalStateException("projection failed");
            }
        }
    }
}
