package io.ddd4j.data.projection.panache;

import io.ddd4j.core.api.R;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
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
    /**
     * 当前测试使用并负责释放的视图或认证管理器。
     */
    @Inject
    ViewManager manager;
    /**
     * 测试使用或记录的多条投影位点。
     */
    @Inject
    QuarkusProjectionPositionRepository positions;
    /**
     * 当前用例注册和执行的投影视图。
     */
    @Inject
    ProjectionViewBean view;
    /**
     * 测试驱动发送或记录的写入命令。
     */
    @Inject
    CommandBus commands;
    /**
     * 当前投影测试使用的原生调度器或替身。
     */
    @Inject
    Scheduler scheduler;
    /**
     * 用于核对重复启动行为的调用次数。
     */
    @Inject
    jakarta.enterprise.event.Event<io.quarkus.runtime.StartupEvent> startups;
    /**
     * 执行 JPA 查询和持久化操作的实体管理器。
     */
    @Inject
    jakarta.persistence.EntityManager entityManager;

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>，<code>ProjectionBeans.readFrom.get()</code> 等于 <code>11L</code>。
     *
     * @throws Exception 准备或释放被测运行资源时失败
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

    /**
     * 验证<code>positions.findByStreamId("r08-command")</code>为空，<code>result.isOk()</code>为假，<code>positions.findByStreamId("r08-command").orElseThrow().getNextEventNumber()</code> 等于 <code>19L</code>。
     */
    @Test
    void commandKeepsExecutorOwnedJtaBoundaryAndFailureResult() {
        positions.deleteByStreamId("r08-command");
        assertThatThrownBy(() -> commands.execute(new TxCommand(true))).hasMessageContaining("command failed");
        assertThat(positions.findByStreamId("r08-command")).isEmpty();
        R<?> result = commands.execute(new TxCommand(false));
        assertThat(result.isOk()).isFalse();
        // 旧 Quarkus 总线没有 R.fail 自动回滚规则；事务归执行器所有。
        assertThat(positions.findByStreamId("r08-command").orElseThrow().getNextEventNumber()).isEqualTo(19L);
    }

    /**
     * 实现 <code>QuarkusTestProfile</code>，供测试调用 <code>getConfigOverrides()</code> 并观察结果。
     */
    public static class DurableProfile implements QuarkusTestProfile {
        /**
         * 通过 <code>Map.of()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>Map.of("ddd4j.quarkus.cqrs.projection.mode", "durable")</code>
         */
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("ddd4j.quarkus.cqrs.projection.mode", "durable");
        }
    }

    /**
     * 供命令总线验证类型匹配和分发的命令样本。
     */
    public static class TxCommand implements Command {
        /**
         * 配置当前替身调用时是否抛出测试异常。
         */
        final boolean throwsException;

        /**
         * 设置 <code>throwsException</code>，用于构造 <code>TxCommand</code> 测试实例。
         *
         * @param throwsException 用于初始化 <code>TxCommand</code> 的 <code>boolean</code> 值
         */
        public TxCommand(boolean throwsException) {
            this.throwsException = throwsException;
        }
    }

    /**
     * 接收测试命令并返回可断言结果的执行器替身。
     */
    @ApplicationScoped
    @IfBuildProperty(name = "ddd4j.quarkus.cqrs.projection.mode", stringValue = "durable")
    public static class TxExecutor implements CommandExecutor<TxCommand> {
        /**
         * 测试使用或记录的多条投影位点。
         */
        @Inject
        QuarkusProjectionPositionRepository positions;

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * <p>测试夹具会调用 <code>Set.of(TxCommand.class)</code>。</p>
         *
         * @return 实际返回<code>Set.of(TxCommand.class)</code>
         */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Set.of(TxCommand.class);
        }

        /**
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>positions.save(new DefaultProjectionPosition("r08-command", 19L))</code>、<code>R.fail("business failure")</code>。</p>
         *
         * @param command 命令对象
         * @return 实际返回<code>R.fail("business failure")</code>
         */
        @Override
        @Transactional
        public R<?> execute(TxCommand command) {
            positions.save(new DefaultProjectionPosition("r08-command", 19L));
            if (command.throwsException) {
                throw new IllegalStateException("command failed");
            }
            return R.fail("business failure");
        }
    }

    /**
     * 向测试容器注册 <code>reader()</code> 等测试入口或组件。
     */
    @ApplicationScoped
    @IfBuildProperty(name = "ddd4j.quarkus.cqrs.projection.mode", stringValue = "durable")
    public static class ProjectionBeans {
        /**
         * 在测试线程之间共享当前操作次数或更新数量的计数器。
         */
        static final AtomicLong readFrom = new AtomicLong(-1);

        /**
         * 调用 <code>readFrom.set()</code>、<code>EventChunk.empty()</code> 执行当前夹具操作。
         *
         * @return 当前测试使用的结果，取自 <code>from &lt; 11 ? new EventChunk&lt;&gt;(List.of(11L), 11) : EventChunk.empty(from)</code>
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

    /**
     * 供运行时测试验证事件应用和位点推进的投影视图。
     */
    @ApplicationScoped
    @IfBuildProperty(name = "ddd4j.quarkus.cqrs.projection.mode", stringValue = "durable")
    public static class ProjectionViewBean implements ProjectionView<Object> {
        /**
         * 测试使用或记录的多条投影位点。
         */
        @Inject
        QuarkusProjectionPositionRepository positions;
        /**
         * 令当前替身在指定测试路径中返回失败。
         */
        volatile boolean fail;

        /**
         * 根据本次调用更新 <code>fail = value</code>，用于后续测试步骤。
         *
         * @param value 当前用例传入的值
         */
        public void setFail(boolean value) {
            fail = value;
        }

        /**
         * 返回当前配置、视图或处理器的名称。
         *
         * @return 实际返回<code>"r08-events"</code>，供调用方取得当前值
         */
        @Override
        public String getName() {
            return "r08-events";
        }

        /**
         * 返回投影视图配置的调度表达式。
         *
         * @return 实际返回<code>"0 0 0 1 JAN ? 2099"</code>，供调用方取得当前值
         */
        @Override
        public String getCron() {
            return "0 0 0 1 JAN ? 2099";
        }

        /**
         * 返回投影或处理器关心的事件类型。
         *
         * <p>测试夹具会调用 <code>Set.of("PositionAdvanced")</code>。</p>
         *
         * @return 实际返回<code>Set.of("PositionAdvanced")</code>
         */
        @Override
        public Collection<String> getEventTypes() {
            return Set.of("PositionAdvanced");
        }

        /**
         * 处理本批投影事件。
         *
         * <p>测试夹具会调用 <code>positions.save(new DefaultProjectionPosition("r08-read-model", (Long)events.iterator().next()))</code>、<code>events.iterator().next()</code>。</p>
         *
         * @param events 事件列表
         */
        @Override
        public void handleEvents(Collection<Object> events) {
            positions.save(new DefaultProjectionPosition("r08-read-model", (Long) events.iterator().next()));
            if (fail) {
                throw new IllegalStateException("projection failed");
            }
        }
    }
}
