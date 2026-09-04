package io.ddd4j.data.cqrs.quarkus;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.ArrayList;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * {@link QuarkusCommandBus} 真实 ArC 容器集成测试（Task 6.4）。
 *
 * <p>真实 Quarkus 容器（{@code @QuarkusTest}，零配置引导——ArC 自带启动容器，
 * 不用 H2／数据源，无 tx 注解配置）＋真实 {@code @ApplicationScoped} 执行器 Bean
 * （零 mock）——完整验证「构造期 {@code Instance} 流式收集 → CommandRegistry
 * 整批注册 → DefaultCommandBus 快照组装」的装配链与 ddd4j-core 的分发契约。
 * 三用例与 SPI 侧 {@code CommandRegistryTest} 及 {@code SpringCommandBusIT}
 * （-spring 模块）对齐：
 * <ol>
 *   <li>execute 返回 Result.success（真实路由到业务 Handler）</li>
 *   <li>多类型执行器之一冲突 → 整批拒绝以 Bean 创建失败传播（同 4.4 注册语义）</li>
 *   <li>未注册命令走 ISE（与 ddd4j-core DefaultCommandBus 一致）</li>
 * </ol>
 *
 * <p>冲突轨说明：单个 {@code @QuarkusTest} 只引导一个应用，若把冲突执行器注册
 * 为真实 Bean 会拖垮所有用例的总线装配，故以 {@link FixedInstance}（固定列表的
 * {@code Instance} 测试替身，仅替「发现」一环）承载冲突对——注册／快照语义
 * 走真实 {@code CommandRegistry}＋{@code DefaultCommandBus} 构造链。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("QuarkusCommandBus 真实 ArC 容器 IT")
@QuarkusTest
class QuarkusCommandBusIT {

    @Inject
    QuarkusCommandBus bus;

    @Test
    void execute_已注册命令_应路由到真实Handler并返回成功() {
        Result<String> result = bus.execute(new SampleCommand());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.data()).contains(SampleCommandHandler.HANDLED);
    }

    @Test
    void 装配期冲突_整批拒绝应传播为Bean创建失败() {
        Instance<CommandExecutor<?>> conflicting = new FixedInstance<>(Arrays.asList(
                new ExclusiveConflictHandler(), new OverlappingMultiTypeHandler()));

        IllegalStateException exception = catchThrowableOfType(
                IllegalStateException.class, () -> new QuarkusCommandBus(conflicting));

        // CommandRegistry 整批拒绝的 ISE 自 QuarkusCommandBus 构造器直接传播
        // （Bean 创建失败——装配期立即暴露，未拦截）
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains(ConflictCommand.class.getName());
    }

    @Test
    void execute_未注册命令_应抛ISE与ddd4j_core一致() {
        IllegalStateException exception = catchThrowableOfType(
                IllegalStateException.class, () -> bus.execute(new UnknownCommand()));

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains(UnknownCommand.class.getName());
    }

    static class ConflictCommand implements Command {
    }

    static class CompanionCommand implements Command {
    }

    static class UnknownCommand implements Command {
    }

    static class ExclusiveConflictHandler implements CommandExecutor<ConflictCommand> {

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.singleton(ConflictCommand.class);
        }

        @Override
        public Result execute(ConflictCommand command) {
            return Result.ok();
        }
    }

    /**
     * 多类型执行器（ConflictCommand + CompanionCommand）：与
     * {@link ExclusiveConflictHandler} 在 ConflictCommand 撞型，验证后注册方
     * 被整批拒绝（CompanionCommand 一并不落库）且 ISE 自构造器传播。
     */
    static class OverlappingMultiTypeHandler implements CommandExecutor<Command> {

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(ConflictCommand.class, CompanionCommand.class)));
        }

        @Override
        public Result execute(Command command) {
            return Result.ok();
        }
    }

    /**
     * 固定列表的 {@link Instance} 测试替身（仅 {@code iterator()} 可用，
     * 其余容器查询操作不支持——本 IT 只经构造器的流式收集消费它）。
     *
     * @param <T> 枚举元素类型
     */
    private static final class FixedInstance<T> implements Instance<T> {

        private final List<T> elements;

        FixedInstance(List<T> elements) {
            this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
        }

        @Override
        public Iterator<T> iterator() {
            return elements.iterator();
        }

        @Override
        public T get() {
            throw new UnsupportedOperationException("fixed test instance");
        }

        @Override
        public Instance<T> select(Annotation... qualifiers) {
            throw new UnsupportedOperationException("fixed test instance");
        }

        @Override
        public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException("fixed test instance");
        }

        @Override
        public <U extends T> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype,
                Annotation... qualifiers) {
            throw new UnsupportedOperationException("fixed test instance");
        }

        @Override
        public boolean isUnsatisfied() {
            return false;
        }

        @Override
        public boolean isAmbiguous() {
            return false;
        }

        @Override
        public void destroy(T instance) {
            throw new UnsupportedOperationException("fixed test instance");
        }

        @Override
        public Handle<T> getHandle() {
            throw new UnsupportedOperationException("fixed test instance");
        }

        @Override
        public Iterable<? extends Handle<T>> handles() {
            throw new UnsupportedOperationException("fixed test instance");
        }
    }
}
