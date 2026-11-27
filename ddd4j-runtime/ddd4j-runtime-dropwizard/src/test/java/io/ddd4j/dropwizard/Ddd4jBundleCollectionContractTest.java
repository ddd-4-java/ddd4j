package io.ddd4j.dropwizard;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.readmodel.*;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.dropwizard.cqrs.DropwizardProjectionScheduler;
import io.ddd4j.runtime.testkit.RuntimeFixtures;
import io.dropwizard.core.Application;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.lifecycle.JettyManaged;
import io.dropwizard.testing.DropwizardTestSupport;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 校验三线 Bundle 对无效服务集合的共同构造契约。
 */
class Ddd4jBundleCollectionContractTest {

    /**
     * 核对<code>thread.isAlive()</code>为假。
     */
    private static void assertNoProjectionThreads() {
        // JDK 在线程返回前标记 executor terminated；等待真实 Thread 退出再断言。
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && thread.getName().equals("dropwizard-projection-scheduler")) {
                try {
                    thread.join(1000);
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
                assertFalse(thread.isAlive(), "Owned projection thread did not exit");
            }
        }
    }

    /**
     * 调用 <code>configuration.getBytes()</code> 执行当前夹具操作。
     *
     * @return 提供给后续契约断言的 <code>DropwizardTestSupport&lt;Configuration&gt;</code> 结果
     */
    private static DropwizardTestSupport<Configuration> support() {
        String configuration = "server:\n"
                + "  type: default\n"
                + "  applicationConnectors:\n"
                + "    - type: http\n"
                + "      port: 0\n"
                + "  adminConnectors:\n"
                + "    - type: http\n"
                + "      port: 0\n";
        return new DropwizardTestSupport<>(NativeApplication.class, "bundle-test.yml",
                ignored -> new ByteArrayInputStream(configuration.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 构造 <code>ProjectionView&lt;Object&gt;</code> 作为当前测试的输入或结果。
     *
     * @param name 当前测试指定的对象或属性名称
     * @param cron 用于构造本次返回结果的 <code>String</code> 输入
     * @param task 用于构造本次返回结果的 <code>Runnable</code> 输入
     * @return 提供给后续契约断言的 <code>ProjectionView&lt;Object&gt;</code> 结果
     */
    private static ProjectionView<Object> view(String name, String cron, Runnable task) {
        return new ProjectionView<Object>() {
            public String getName() {
                return name;
            }

            public String getCron() {
                return cron;
            }

            public Collection<String> getEventTypes() {
                return Collections.singletonList("Order");
            }

            public void handleEvents(Collection<Object> events) {
                task.run();
            }
        };
    }

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void constructorRejectsNullElementsInServiceCollections() {
        assertThrows(NullPointerException.class, () -> new Ddd4jBundle<>(
                Collections.<CommandExecutor<?>>singletonList(null), Collections.<Consumer<Object>>emptyList(),
                null, I18nProvider.DEFAULT, Collections.<ReadinessContributor>emptyList()));
        assertThrows(NullPointerException.class, () -> new Ddd4jBundle<>(
                Collections.<CommandExecutor<?>>emptyList(), Collections.<Consumer<Object>>singletonList(null),
                null, I18nProvider.DEFAULT, Collections.<ReadinessContributor>emptyList()));
        assertThrows(NullPointerException.class, () -> new Ddd4jBundle<>(
                Collections.<CommandExecutor<?>>emptyList(), Collections.<Consumer<Object>>emptyList(),
                null, I18nProvider.DEFAULT, Collections.<ReadinessContributor>singletonList(null)));
    }

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>，<code>bundle.withProjection(positions, null, Collections.emptyList())</code> 抛出 <code>NullPointerException.class</code>，<code>BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class)</code> 等于 <code>previous</code>。
     */
    @Test
    void explicitMissingReaderAndRepositoryFailBeforeResourcesOrGlobals() {
        Object previous = BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class);
        Ddd4jBundle<Configuration> bundle = new Ddd4jBundle<>();
        Positions positions = new Positions();
        assertThrows(NullPointerException.class, () -> bundle.withProjection(null, (s, f, n, t) -> EventChunk.empty(f), Collections.emptyList()));
        assertThrows(NullPointerException.class, () -> bundle.withProjection(positions, null, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> bundle.withProjection(positions, new NoopEventChunkReader<>(), Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> bundle.withProjection(new InMemoryProjectionPositionRepository(), (s, f, n, t) -> EventChunk.empty(f), Collections.emptyList()));
        assertFalse(bundle.runtime().isPresent());
        assertEquals(previous, BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class));
    }

    /**
     * 验证<code>support.getLocalPort() &gt; 0</code>为真，<code>managed</code> 等于 <code>1</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void nativeBasicBundleRegistersOneManagedAndNoProjectionResources() throws Exception {
        NativeApplication.bundle = new Ddd4jBundle<>();
        DropwizardTestSupport<Configuration> support = support();
        Object previous = BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class);
        try {
            support.before();
            assertTrue(support.getLocalPort() > 0);
            assertFalse(NativeApplication.bundle.runtime().orElseThrow(NoSuchElementException::new).projectionManager().isPresent());
            long managed = support.getEnvironment().lifecycle().getManagedObjects().stream()
                    .filter(value -> value instanceof JettyManaged && ((JettyManaged) value).getManaged() instanceof Ddd4jDropwizardRuntime).count();
            assertEquals(1, managed);
            assertTrue(support.getEnvironment().healthChecks().getNames().contains("ddd4j-readiness"));
            assertNoProjectionThreads();
        } finally {
            support.after();
        }
        assertEquals(previous, BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class));
    }

    /**
     * 验证<code>applied.await(3, TimeUnit.SECONDS)</code>为真，<code>positions.position.get()</code> 等于 <code>20</code>，<code>positions.position.get()</code> 等于 <code>21</code>。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void nativeManagedStartsOneProjectionRestartsAndStopsOwnedThreads() throws Exception {
        Positions positions = new Positions();
        CountDownLatch applied = new CountDownLatch(1);
        NativeApplication.bundle = new Ddd4jBundle<Configuration>().withProjection(positions,
                (s, f, n, t) -> f < 20 ? new EventChunk<>(Collections.singletonList("event"), 20) : EventChunk.empty(f),
                Collections.singletonList(view("orders", "* * * * * *", applied::countDown)));
        DropwizardTestSupport<Configuration> support = support();
        Object previous = BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class);
        try {
            support.before();
            Ddd4jDropwizardRuntime runtime = NativeApplication.bundle.runtime().orElseThrow(NoSuchElementException::new);
            assertTrue(runtime.projectionManager().orElseThrow(NoSuchElementException::new).isRunning());
            runtime.start();
            assertTrue(applied.await(3, TimeUnit.SECONDS));
            runtime.stop(); // shutdown 等待整个 Runner（含 save）退出。
            assertEquals(20, positions.position.get());
            runtime.start();
            assertTrue(runtime.projectionManager().orElseThrow(NoSuchElementException::new).isRunning());
        } finally {
            support.after();
        }
        assertFalse(NativeApplication.bundle.runtime().orElseThrow(NoSuchElementException::new).projectionManager().orElseThrow(NoSuchElementException::new).isRunning());
        assertEquals(previous, BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class));
        assertNoProjectionThreads();
        positions.save(new DefaultProjectionPosition("orders", 21));
        assertEquals(21, positions.position.get());
    }

    /**
     * 验证被测调用 抛出 <code>Exception.class</code>，<code>schedules.get()</code> 等于 <code>2</code>，<code>callback.await(3, TimeUnit.SECONDS)</code>为真。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void nativePartialStartCancelsFirstHandleRestoresOwnerAndLeavesBorrowedSchedulerUsable() throws Exception {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        Ddd4jDropwizardRuntime previous = new Ddd4jDropwizardRuntime(fixtures.publisher(), fixtures.subjectProvider(), fixtures.i18nProvider(), fixtures.commandBus());
        previous.start();
        ProjectionRunner<Object> runner = new ProjectionRunner<>(new DefaultProjectionService(new InMemoryProjectionPositionRepository()), new NoopEventChunkReader<>());
        DropwizardProjectionScheduler borrowed = DropwizardProjectionScheduler.create(new Environment("borrowed"), Collections.emptyList(), runner);
        List<ViewScheduler.ViewScheduleHandle> handles = new java.util.ArrayList<>();
        AtomicInteger schedules = new AtomicInteger();
        ViewScheduler failureScheduler = (name, cron, task) -> {
            if (schedules.incrementAndGet() == 2) {
                throw new IllegalStateException("second registration failed");
            }
            ViewScheduler.ViewScheduleHandle handle = borrowed.schedule(name, cron, task);
            handles.add(handle);
            return handle;
        };
        NativeApplication.bundle = new Ddd4jBundle<Configuration>().withProjection(new Positions(),
                (s, f, n, t) -> EventChunk.empty(f), Arrays.asList(view("first", "* * * * * *", () -> {
                }), view("second", "* * * * * *", () -> {
                })), failureScheduler);
        DropwizardTestSupport<Configuration> support = support();
        try {
            assertThrows(Exception.class, support::before);
            assertEquals(2, schedules.get());
            assertEquals(1, handles.size());
            assertFalse(handles.get(0).isActive());
            assertFalse(NativeApplication.bundle.runtime().orElseThrow(NoSuchElementException::new).projectionManager().orElseThrow(NoSuchElementException::new).isRunning());
            assertSame(fixtures.commandBus(), BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class).orElseThrow(NoSuchElementException::new));
            CountDownLatch callback = new CountDownLatch(1);
            borrowed.schedule("usable-after-failure", "* * * * * *", callback::countDown);
            assertTrue(callback.await(3, TimeUnit.SECONDS));
        } finally {
            support.after();
            borrowed.shutdown();
            previous.stop();
        }
        assertNoProjectionThreads();
    }

    /**
     * 验证被测调用 抛出 <code>Exception.class</code>，<code>BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class)</code> 等于 <code>previous</code>。
     */
    @Test
    void nativeOwnedPartialStartReleasesItsExecutor() {
        NativeApplication.bundle = new Ddd4jBundle<Configuration>().withProjection(new Positions(),
                (s, f, n, t) -> EventChunk.empty(f), Arrays.asList(view("first", "* * * * * *", () -> {
                }), view("second", "invalid", () -> {
                })));
        DropwizardTestSupport<Configuration> support = support();
        Object previous = BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class);
        try {
            assertThrows(Exception.class, support::before);
        } finally {
            support.after();
        }
        assertFalse(NativeApplication.bundle.runtime().orElseThrow(NoSuchElementException::new).projectionManager().orElseThrow(NoSuchElementException::new).isRunning());
        assertEquals(previous, BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class));
        assertNoProjectionThreads();
    }

    /**
     * 原生启动夹具，无业务 API；H2 持久化证据另由当前 JAR 探针覆盖。
     */
    public static final class NativeApplication extends Application<Configuration> {
        /**
         * 供测试启动 Dropwizard 商品交易运行时的 Bundle。
         */
        static Ddd4jBundle<Configuration> bundle;

        /**
         * 调用 <code>bootstrap.addBundle()</code> 执行当前夹具操作。
         *
         * @param bootstrap 传给 <code>bootstrap.addBundle()</code> 的 <code>Bootstrap&lt;Configuration&gt;</code> 输入
         */
        @Override
        public void initialize(Bootstrap<Configuration> bootstrap) {
            bootstrap.addBundle(bundle);
        }

        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         *
         * @param configuration 本次调用使用的 <code>Configuration</code> 参数 <code>configuration</code>
         * @param environment   本次调用使用的 <code>Environment</code> 参数 <code>environment</code>
         */
        @Override
        public void run(Configuration configuration, Environment environment) {
        }
    }

    /**
     * 仅测试使用的确定性位置夹具，不作为产品持久仓储。
     */
    private static final class Positions implements ProjectionPositionRepository {
        /**
         * 测试使用或记录的投影位点。
         */
        final AtomicLong position = new AtomicLong(10);

        /**
         * 通过 <code>Optional.of()</code> 取得当前测试需要的结果。
         *
         * @param stream 传给 <code>Optional.of()</code> 的 <code>String</code> 输入
         * @return 当前测试使用的结果，取自 <code>Optional.of(new DefaultProjectionPosition(stream, position.get()))</code>
         */
        public Optional<ProjectionPosition> findByStreamId(String stream) {
            return Optional.of(new DefaultProjectionPosition(stream, position.get()));
        }

        /**
         * 通过 <code>Collections.singletonList()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>Collections.singletonList(new DefaultProjectionPosition("orders", position.get()))</code>
         */
        public List<ProjectionPosition> findAll() {
            return Collections.singletonList(new DefaultProjectionPosition("orders", position.get()));
        }

        /**
         * 读取 <code>value</code> 供当前测试使用。
         *
         * @param value 当前用例传入的值
         * @return 当前测试使用的结果，取自 <code>value</code>
         */
        public ProjectionPosition save(ProjectionPosition value) {
            position.set(value.getNextEventNumber());
            return value;
        }

        /**
         * 调用 <code>position.set()</code> 执行当前夹具操作。
         *
         * @param stream 本次调用使用的 <code>String</code> 参数 <code>stream</code>
         */
        public void deleteByStreamId(String stream) {
            position.set(0);
        }

        /**
         * 调用 <code>position.set()</code> 执行当前夹具操作。
         *
         * @param stream 本次调用使用的 <code>String</code> 参数 <code>stream</code>
         */
        public void resetToZero(String stream) {
            position.set(0);
        }
    }
}
