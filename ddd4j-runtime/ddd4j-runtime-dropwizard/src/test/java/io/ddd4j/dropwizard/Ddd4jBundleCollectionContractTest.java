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

    /** \u6838\u5bf9<code>thread.isAlive()</code>\u4e3a\u5047\u3002 */
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
     * \u8c03\u7528 <code>configuration.getBytes()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>DropwizardTestSupport&lt;Configuration&gt;</code> \u7ed3\u679c
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
     * \u6784\u9020 <code>ProjectionView&lt;Object&gt;</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
     * @param cron \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>String</code> \u8f93\u5165
     * @param task \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>Runnable</code> \u8f93\u5165
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>ProjectionView&lt;Object&gt;</code> \u7ed3\u679c
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>bundle.withProjection(positions, null, Collections.emptyList())</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class)</code> \u7b49\u4e8e <code>previous</code>\u3002 */
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
     * \u9a8c\u8bc1<code>support.getLocalPort() &gt; 0</code>\u4e3a\u771f\uff0c<code>managed</code> \u7b49\u4e8e <code>1</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>applied.await(3, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>positions.position.get()</code> \u7b49\u4e8e <code>20</code>\uff0c<code>positions.position.get()</code> \u7b49\u4e8e <code>21</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>Exception.class</code>\uff0c<code>schedules.get()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>callback.await(3, TimeUnit.SECONDS)</code>\u4e3a\u771f\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>Exception.class</code>\uff0c<code>BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class)</code> \u7b49\u4e8e <code>previous</code>\u3002 */
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
        /** \u4f9b\u6d4b\u8bd5\u542f\u52a8 Dropwizard \u5546\u54c1\u4ea4\u6613\u8fd0\u884c\u65f6\u7684 Bundle\u3002 */
        static Ddd4jBundle<Configuration> bundle;

        /**
         * \u8c03\u7528 <code>bootstrap.addBundle()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param bootstrap \u4f20\u7ed9 <code>bootstrap.addBundle()</code> \u7684 <code>Bootstrap&lt;Configuration&gt;</code> \u8f93\u5165
         */
        @Override
        public void initialize(Bootstrap<Configuration> bootstrap) {
            bootstrap.addBundle(bundle);
        }

        /**
         * \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002
         * @param configuration \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>Configuration</code> \u53c2\u6570 <code>configuration</code>
         * @param environment \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>Environment</code> \u53c2\u6570 <code>environment</code>
         */
        @Override
        public void run(Configuration configuration, Environment environment) {
        }
    }

    /**
     * 仅测试使用的确定性位置夹具，不作为产品持久仓储。
     */
    private static final class Positions implements ProjectionPositionRepository {
        /** \u6d4b\u8bd5\u4f7f\u7528\u6216\u8bb0\u5f55\u7684\u6295\u5f71\u4f4d\u70b9\u3002 */
        final AtomicLong position = new AtomicLong(10);

        /**
         * \u901a\u8fc7 <code>Optional.of()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param stream \u4f20\u7ed9 <code>Optional.of()</code> \u7684 <code>String</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Optional.of(new DefaultProjectionPosition(stream, position.get()))</code>
         */
        public Optional<ProjectionPosition> findByStreamId(String stream) {
            return Optional.of(new DefaultProjectionPosition(stream, position.get()));
        }

        /**
         * \u901a\u8fc7 <code>Collections.singletonList()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Collections.singletonList(new DefaultProjectionPosition("orders", position.get()))</code>
         */
        public List<ProjectionPosition> findAll() {
            return Collections.singletonList(new DefaultProjectionPosition("orders", position.get()));
        }

        /**
         * \u8bfb\u53d6 <code>value</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param value \u5f53\u524d\u7528\u4f8b\u4f20\u5165\u7684\u503c
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>value</code>
         */
        public ProjectionPosition save(ProjectionPosition value) {
            position.set(value.getNextEventNumber());
            return value;
        }

        /**
         * \u8c03\u7528 <code>position.set()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param stream \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>String</code> \u53c2\u6570 <code>stream</code>
         */
        public void deleteByStreamId(String stream) {
            position.set(0);
        }

        /**
         * \u8c03\u7528 <code>position.set()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param stream \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>String</code> \u53c2\u6570 <code>stream</code>
         */
        public void resetToZero(String stream) {
            position.set(0);
        }
    }
}
