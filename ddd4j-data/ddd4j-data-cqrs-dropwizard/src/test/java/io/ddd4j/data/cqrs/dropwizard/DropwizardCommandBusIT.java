package io.ddd4j.data.cqrs.dropwizard;

import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashSet;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.setup.Environment;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.testing.DropwizardTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * {@link DropwizardCommandBus} 真实 Dropwizard 启动 + ServiceLoader 发现集成测试
 * （Task 6.9）。
 *
 * <p>真实发现链（test classpath 的
 * {@code META-INF/services/io.ddd4j.core.cqrs.command.CommandExecutor} 注册样例执行器，
 * JDK {@link ServiceLoader} 迭代成集合供入 {@code create} 工厂）＋真实执行器服务
 * （零 mock）——完整验证「真实 Dropwizard 5 启动（{@link DropwizardTestSupport}
 * 驱动真实 Jetty/Jersey 容器，随机端口）→ {@code Application.run} 内
 * {@code create} 工厂装配 → ServiceLoader 迭代收集 → CommandRegistry 整批注册 →
 * DefaultCommandBus 快照组装」的装配链与 ddd4j-core 的分发契约。三用例与 SPI 侧
 * {@code CommandRegistryTest} 及 {@code SpringCommandBusIT}／
 * {@code HelidonCommandBusIT}／{@code JavalinCommandBusIT} 对齐：
 * <ol>
 *   <li>run 装配的总线 execute 返回 Result.success（真实路由到业务 Handler，
 *       另以 local port &gt; 0 佐证真实 Jetty 已监听）</li>
 *   <li>多类型执行器之一冲突 → 整批拒绝的 ISE 自工厂传播（同 4.4 注册语义）</li>
 *   <li>未注册命令走 ISE（与 ddd4j-core DefaultCommandBus 一致）</li>
 * </ol>
 *
 * <p><b>启动开销注记</b>：Dropwizard 真实启动（Jetty+Jersey+Jackson+metrics 全栈）
 * 比 Javalin/Vert.x 重一个量级——刻意 {@link BeforeAll} 类级单次启动（3 用例共享
 * 同一真实容器，brief 允许「3 用例 ≥1 真实启动」），总线本身无共享可变状态
 * （构造即快照），用例间以「每用例独立 assemble 工厂调用」保持隔离。
 *
 * <p>发现轨说明：Dropwizard 无容器——候选由 JDK SPI 枚举（集成方供入 {@code create}
 * 的 {@code Collection} 即本测试的 ServiceLoader 迭代产物，与生产接法一致，
 * {@link TestApplication#run} 即生产接法的样例化）；总线 {@code execute} 为同步调用，
 * 不依赖 Dropwizard 的 HTTP 回路（IT 不注册任何 Jersey 资源，验证分发与 Web 层解耦）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("DropwizardCommandBus 真实 Dropwizard 启动 + ServiceLoader 发现 IT")
class DropwizardCommandBusIT {

    /**
     * 真实 Dropwizard 5 测试支撑（随机端口驱动真实 Jetty/Jersey 容器启动，
     * IT 全程生命周期自管——类级单次启动）。
     */
    private static DropwizardTestSupport<Configuration> support;

    @BeforeAll
    static void startDropwizard() throws Exception {
        support = new DropwizardTestSupport<>(TestApplication.class, randomPortConfiguration());
        support.before();
    }

    @AfterAll
    static void stopDropwizard() {
        if (support != null) {
            support.after();
        }
    }

    /**
     * 每用例独立装配总线（构造即快照、无共享可变状态），保持用例间隔离。
     * 发现轨为真实 JDK {@link ServiceLoader}（{@link #discover}）。
     *
     * @return 经真实 ServiceLoader 发现装配的总线
     */
    private DropwizardCommandBus assemble() {
        return DropwizardCommandBus.create(support.getApplication(), discover());
    }

    /**
     * 真实发现轨：迭代 test classpath 的
     * {@code META-INF/services/io.ddd4j.core.cqrs.command.CommandExecutor} 声明成
     * 候选集合（本方法即 raw 泛型收口点，集成方同款接法见
     * {@code DropwizardCommandBus} javadoc；{@link TestApplication#run} 复用同一
     * 发现轨，保持生产接法样例与测试装配一致）。
     *
     * @return ServiceLoader 发现的执行器候选集合
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<CommandExecutor<?>> discover() {
        List<CommandExecutor<?>> executors = new ArrayList<>();
        for (CommandExecutor executor : ServiceLoader.load(CommandExecutor.class)) {
            executors.add(executor);
        }
        return executors;
    }

    /**
     * 随机端口配置（application/admin 连接器 port 0 → 内核分配空闲端口），
     * 避免真实 Jetty 启动撞固定 8080/8081。
     *
     * @return 随机端口的 Dropwizard 配置
     */
    private static Configuration randomPortConfiguration() {
        Configuration configuration = new Configuration();
        DefaultServerFactory serverFactory = (DefaultServerFactory) configuration.getServerFactory();
        HttpConnectorFactory applicationConnector = new HttpConnectorFactory();
        applicationConnector.setPort(0);
        serverFactory.setApplicationConnectors(Collections.singletonList(applicationConnector));
        HttpConnectorFactory adminConnector = new HttpConnectorFactory();
        adminConnector.setPort(0);
        serverFactory.setAdminConnectors(Collections.singletonList(adminConnector));
        return configuration;
    }

    @Test
    void execute_已注册命令_应路由到真实Handler并返回成功() {
        // run 装配轨：TestApplication.run 内经 create(this, discover()) 组装的生产
        // 接法总线——真实 Dropwizard 启动期已装配完成（getApplication 的泛型 A
        // 经赋值目标推断为 TestApplication，方能访问 busFromRun 访问器）。
        TestApplication application = support.getApplication();
        DropwizardCommandBus bus = application.busFromRun();

        Result<String> result = bus.execute(new SampleCommand());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.data()).contains(SampleCommandHandler.HANDLED);
        // 佐证真实容器在监听（DropwizardTestSupport 驱动的是真实 Jetty，
        // local port 经 port 0 由内核分配，非 -1 占位）。
        assertThat(support.getLocalPort()).isPositive();
    }

    @Test
    void 装配期冲突_整批拒绝应自工厂传播() {
        // 供入「多类型占位执行器（SampleCommand + CompanionCommand）+ 真实
        // ServiceLoader 发现的候选集合」：占位方与 services 文件中的
        // SampleCommandHandler 在 SampleCommand 撞型 → CommandRegistry 整批拒绝的
        // ISE 自 create 工厂直接传播（装配期立即暴露，未拦截）。
        List<CommandExecutor<?>> executors = new ArrayList<>();
        executors.add(new OverlappingMultiTypeHandler());
        executors.addAll(discover());

        IllegalStateException exception = catchThrowableOfType(
                IllegalStateException.class,
                () -> DropwizardCommandBus.create(support.getApplication(), executors));

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains(SampleCommand.class.getName());
    }

    @Test
    void execute_未注册命令_应抛ISE与ddd4j_core一致() {
        DropwizardCommandBus bus = assemble();

        IllegalStateException exception = catchThrowableOfType(
                IllegalStateException.class, () -> bus.execute(new UnknownCommand()));

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains(UnknownCommand.class.getName());
    }

    static class CompanionCommand implements Command {
    }

    static class UnknownCommand implements Command {
    }

    /**
     * 多类型占位执行器（SampleCommand + CompanionCommand）：与 services 文件中的
     * {@link SampleCommandHandler} 在 SampleCommand 撞型，验证后供入方被整批拒绝
     * （CompanionCommand 一并不落库）且 ISE 自工厂传播。刻意不注册进
     * services 文件（占位方经 {@code create} 的 Collection 直入，撞型由发现轨触发）。
     */
    static class OverlappingMultiTypeHandler implements CommandExecutor<Command> {

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(SampleCommand.class, CompanionCommand.class)));
        }

        @Override
        public Result execute(Command command) {
            return Result.ok();
        }
    }

    /**
     * 测试用 Dropwizard 应用（仅 {@code run} 阶段被覆盖，brief 要求）：
     * 在 {@code Application.run} 内以生产接法调
     * {@link DropwizardCommandBus#create(Application, Collection)}——ServiceLoader
     * 发现候选 → 整批注册 → 快照组装，总线随真实启动完成装配（装配锚点即
     * {@code this} 应用实例）。不注册任何 Jersey 资源／health check／metrics。
     */
    public static class TestApplication extends Application<Configuration> {

        private volatile DropwizardCommandBus busFromRun;

        @Override
        public void run(Configuration configuration, Environment environment) {
            this.busFromRun = DropwizardCommandBus.create(this, discover());
        }

        DropwizardCommandBus busFromRun() {
            return busFromRun;
        }
    }
}
