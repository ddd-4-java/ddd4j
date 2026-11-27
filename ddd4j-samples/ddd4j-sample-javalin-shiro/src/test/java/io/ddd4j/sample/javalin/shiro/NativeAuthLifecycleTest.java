package io.ddd4j.sample.javalin.shiro;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.ddd4j.auth.shiro.subject.ShiroSubject;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.SpiRegistrationScope;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.subject.SubjectStrategy;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.guice.Ddd4jGuiceModule;
import io.ddd4j.guice.context.GuiceContext;
import io.ddd4j.guice.cqrs.GuiceViewManager;
import io.ddd4j.sample.javalin.shiro.config.AuthModule;
import io.ddd4j.sample.javalin.shiro.goods.domain.Goods;
import io.ddd4j.sample.javalin.shiro.goods.domain.GoodsRepository;
import io.ddd4j.sample.javalin.shiro.goods.infrastructure.InMemoryGoodsRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryPermissionRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryRoleRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryUserRepository;
import io.ddd4j.sample.javalin.shiro.rbac.service.RbacService;
import io.javalin.Javalin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.DefaultSessionManager;
import org.apache.shiro.session.mgt.eis.MemorySessionDAO;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>NativeAuthLifecycle</code> \u5728<code>restoresCallerBindings</code>\u3001<code>closeRestoresCallerState</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class NativeAuthLifecycleTest {
    /**
     * \u901a\u8fc7 <code>servletProxy()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param <T> \u5f53\u524d\u5951\u7ea6\u6d4b\u8bd5\u6240\u4f7f\u7528\u7684 Java \u7c7b\u578b
     * @param type \u7528\u6765\u89e3\u6790\u3001\u68c0\u67e5\u6216\u6784\u9020\u6d4b\u8bd5\u5bf9\u8c61\u7684 Java \u7c7b\u578b
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>servletProxy(type, null)</code>
     */
    private static <T> T servletProxy(Class<T> type) {
        return servletProxy(type, null);
    }

    /**
     * \u6309 <code>("getHeader".equals(method.getName()) &amp;&amp; "ddd4j-token".equals(args[0]))</code> \u7b49\u5b9e\u9645\u6761\u4ef6\u9009\u62e9\u5f53\u524d\u7528\u4f8b\u8fd4\u56de\u7684\u7ed3\u679c\u3002
     * @param <T> \u5f53\u524d\u5951\u7ea6\u6d4b\u8bd5\u6240\u4f7f\u7528\u7684 Java \u7c7b\u578b
     * @param type \u7528\u6765\u89e3\u6790\u3001\u68c0\u67e5\u6216\u6784\u9020\u6d4b\u8bd5\u5bf9\u8c61\u7684 Java \u7c7b\u578b
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>token</code>\u3001<code>fail("unexpected servlet call: " + method)</code>
     */
    private static <T> T servletProxy(Class<T> type, String token) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    if ("getHeader".equals(method.getName()) && "ddd4j-token".equals(args[0])) {
                        return token;
                    }
                    return fail("unexpected servlet call: " + method);
                }));
    }

    /**
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>actual</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>failure</code>\uff0c<code>observations</code> \u7b49\u4e8e <code>List.of(Optional.of("framework-context-present"))</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
    @Test
    void restoresCallerBindingsWhenSubjectProviderThrows() throws Exception {
        List<Optional<String>> observations = new ArrayList<>();
        var failure = new IllegalStateException("subject provider failure");
        var previousShiroResources = org.apache.shiro.util.ThreadContext.getResources();
        var manager = new DefaultSecurityManager();
        try (var callerScope = ThreadContext.open();
             var lifecycle = new NativeAuthLifecycle(manager, observations::add)) {
            observations.clear();
            ThreadContext.put("caller", new Object());
            var callerResources = ThreadContext.getResources();

            var callerSubject = new org.apache.shiro.subject.Subject.Builder(manager).buildSubject();
            org.apache.shiro.util.ThreadContext.bind(callerSubject);
            var callerShiroResources = org.apache.shiro.util.ThreadContext.getResources();

            // 仅替换主体解析，避免为这个失败路径注册一套全局 Guice 运行时。
            var field = NativeAuthLifecycle.class.getDeclaredField("provider");
            field.setAccessible(true);
            field.set(lifecycle, new SubjectProvider() {
                @Override
                public Subject getSubject() {
                    ThreadContext.put("request", new Object());
                    throw failure;
                }
            });

            var actual = assertThrows(IllegalStateException.class, () -> lifecycle.doFilter(
                    servletProxy(HttpServletRequest.class), servletProxy(HttpServletResponse.class),
                    (request, response) -> fail("request must stop when its subject cannot be resolved")));

            assertAll(
                    () -> assertSame(failure, actual),
                    () -> assertEquals(callerResources, ThreadContext.getResources()),
                    () -> assertSame(callerSubject, org.apache.shiro.util.ThreadContext.getSubject()),
                    () -> assertEquals(callerShiroResources, org.apache.shiro.util.ThreadContext.getResources()),
                    () -> assertEquals(List.of(Optional.of("framework-context-present")), observations));
        } finally {
            org.apache.shiro.util.ThreadContext.remove();
            org.apache.shiro.util.ThreadContext.setResources(previousShiroResources);
        }
    }

    /** \u9a8c\u8bc1<code>GuiceContext.getInjector()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>injector</code>\uff0c<code>SubjectKit.subjectProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>injector.getInstance(SubjectProvider.class)</code>\uff0c<code>org.apache.shiro.util.ThreadContext.getResources()</code> \u7b49\u4e8e <code>callerShiroResources</code>\u3002 */
    @Test
    void closeRestoresCallerStateWhenOwnedRuntimeThrows() {
        IllegalStateException failure = new IllegalStateException("owned Guice runtime close failure");
        AtomicInteger runtimeCloses = new AtomicInteger();
        AtomicInteger managerDestroys = new AtomicInteger();
        DefaultSecurityManager manager = new DefaultSecurityManager() {
            @Override
            public void destroy() {
                managerDestroys.incrementAndGet();
                super.destroy();
            }
        };
        DefaultSecurityManager callerManager = new DefaultSecurityManager();
        SubjectProvider originalProvider = SubjectKit.subjectProvider;
        SubjectDataProvider originalData = SubjectKit.dataProvider;
        SubjectStrategy originalStrategy = SubjectKit.strategy;
        Injector originalInjector = GuiceContext.isInitialized() ? GuiceContext.getInjector() : null;
        Map<Object, Object> originalShiroResources = org.apache.shiro.util.ThreadContext.getResources();

        Injector callerInjector = Guice.createInjector();
        SubjectProvider callerProvider = new SubjectProvider() {
        };
        SubjectDataProvider callerData = new SubjectDataProvider() {
        };
        SubjectStrategy callerStrategy = new SubjectStrategy();
        InMemoryGoodsRepository callerRepository = new InMemoryGoodsRepository();
        String repositoryKey = RepositoryRegistry.key(Goods.class);
        try (ThreadContext.Scope original = ThreadContext.open();
             SpiRegistrationScope callerSpi = new SpiRegistrationScope()) {
            SubjectKit.register(callerProvider);
            SubjectKit.setDataProvider(callerData);
            SubjectKit.setStrategy(callerStrategy);
            GuiceContext.setInjector(callerInjector);
            callerSpi.register(repositoryKey, Repository.class, callerRepository)
                    .register(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, callerProvider)
                    .start();

            try (NativeAuthLifecycle lifecycle = new NativeAuthLifecycle(manager, ignored -> {
            })) {
                InMemoryUserRepository users = new InMemoryUserRepository();
                InMemoryRoleRepository roles = new InMemoryRoleRepository();
                InMemoryPermissionRepository permissions = new InMemoryPermissionRepository();
                RbacService rbac = new RbacService(users, roles, permissions);
                Injector injector = Guice.createInjector(Ddd4jGuiceModule.basic(),
                        new AuthModule(users, roles, permissions, rbac), new AbstractModule() {
                            @Override
                            protected void configure() {
                                bind(Subject.class).toInstance(new ShiroSubject());
                                bind(GuiceViewManager.class).toInstance(new GuiceViewManager() {
                                    @Override
                                    public void close() {
                                        runtimeCloses.incrementAndGet();
                                        super.close();
                                        throw failure;
                                    }
                                });
                            }
                        });
                lifecycle.attach(injector);
                GoodsRepository appRepository = injector.getInstance(GoodsRepository.class);
                lifecycle.repositories.register(repositoryKey, Repository.class, appRepository)
                        .start();
                Javalin app = Javalin.create();
                lifecycle.attach(app);
                app.start("127.0.0.1", 0);

                ThreadContext.put(new Object(), new Object());
                ThreadContext.set("caller-value", "preserved");
                Map<Object, Object> callerResources = ThreadContext.getResources();
                org.apache.shiro.subject.Subject callerSubject =
                        new org.apache.shiro.subject.Subject.Builder(callerManager).buildSubject();
                org.apache.shiro.util.ThreadContext.bind(callerManager);
                org.apache.shiro.util.ThreadContext.bind(callerSubject);
                Map<Object, Object> callerShiroResources = org.apache.shiro.util.ThreadContext.getResources();

                assertSame(injector, GuiceContext.getInjector());
                assertSame(injector.getInstance(SubjectProvider.class), SubjectKit.subjectProvider);
                assertSame(injector.getInstance(SubjectDataProvider.class), SubjectKit.dataProvider);
                assertSame(SubjectStrategy.instance, SubjectKit.strategy);
                assertSame(appRepository, BaseContext.get(repositoryKey, Repository.class).orElse(null));
                assertTrue(app.jettyServer().server().isStarted());

                IllegalStateException actual = assertThrows(IllegalStateException.class, lifecycle::close);
                lifecycle.close();

                assertAll(
                        () -> assertSame(failure, actual),
                        () -> assertEquals(1, runtimeCloses.get()),
                        () -> assertEquals(1, managerDestroys.get()),
                        () -> assertTrue(app.jettyServer().server().isStopped()),
                        () -> assertSame(callerRepository, BaseContext.get(repositoryKey, Repository.class).orElse(null)),
                        () -> assertSame(callerProvider, BaseContext.get(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class).orElse(null)),
                        () -> assertSame(callerInjector,
                                GuiceContext.isInitialized() ? GuiceContext.getInjector() : null),
                        () -> assertSame(callerProvider, SubjectKit.subjectProvider),
                        () -> assertSame(callerData, SubjectKit.dataProvider),
                        () -> assertSame(callerStrategy, SubjectKit.strategy),
                        () -> assertEquals(callerResources, ThreadContext.getResources()),
                        () -> assertEquals(callerResources, ThreadContext.getValues()),
                        () -> assertSame(callerSubject, org.apache.shiro.util.ThreadContext.getSubject()),
                        () -> assertSame(callerManager, org.apache.shiro.util.ThreadContext.getSecurityManager()),
                        () -> assertEquals(callerShiroResources, org.apache.shiro.util.ThreadContext.getResources()));
            }
        } finally {
            if (Objects.nonNull(originalInjector)) {
                GuiceContext.setInjector(originalInjector);
            } else {
                GuiceContext.clear();
            }
            SubjectKit.subjectProvider = originalProvider;
            SubjectKit.dataProvider = originalData;
            SubjectKit.strategy = originalStrategy;
            org.apache.shiro.util.ThreadContext.remove();
            org.apache.shiro.util.ThreadContext.setResources(originalShiroResources);
            callerManager.destroy();
        }
    }

    /**
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>actual</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>failure</code>\uff0c<code>observedShiro</code> \u7b49\u4e8e <code>Collections.singletonList(callerShiroResources)</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
    @Test
    void restoresCallerBindingsWhenNativeSessionLookupThrows() throws Exception {
        IllegalStateException failure = new IllegalStateException("native session lookup failure");
        DefaultSecurityManager manager = new DefaultSecurityManager();
        DefaultSessionManager sessionManager = new DefaultSessionManager();
        sessionManager.setSessionDAO(new MemorySessionDAO() {
            @Override
            protected Session doReadSession(Serializable sessionId) {
                ThreadContext.put("request-session", sessionId);
                org.apache.shiro.util.ThreadContext.put("request-session", sessionId);
                throw failure;
            }
        });
        manager.setSessionManager(sessionManager);
        DefaultSecurityManager callerManager = new DefaultSecurityManager();
        Map<Object, Object> originalShiroResources = org.apache.shiro.util.ThreadContext.getResources();
        List<Map<Object, Object>> observedFramework = new ArrayList<>();
        List<Map<Object, Object>> observedShiro = new ArrayList<>();

        try (ThreadContext.Scope original = ThreadContext.open();
             NativeAuthLifecycle lifecycle = new NativeAuthLifecycle(manager, ignored -> {
                 observedFramework.add(ThreadContext.getResources());
                 observedShiro.add(org.apache.shiro.util.ThreadContext.getResources());
             })) {
            observedFramework.clear();
            observedShiro.clear();
            ThreadContext.put("caller", new Object());
            org.apache.shiro.subject.Subject callerSubject =
                    new org.apache.shiro.subject.Subject.Builder(callerManager).buildSubject();
            org.apache.shiro.util.ThreadContext.bind(callerManager);
            org.apache.shiro.util.ThreadContext.bind(callerSubject);
            Map<Object, Object> callerResources = ThreadContext.getResources();
            Map<Object, Object> callerShiroResources = org.apache.shiro.util.ThreadContext.getResources();

            Field field = NativeAuthLifecycle.class.getDeclaredField("provider");
            field.setAccessible(true);
            field.set(lifecycle, new SubjectProvider() {
                @Override
                public Subject getSubject() {
                    return fail("provider must not run when native session lookup fails");
                }
            });

            IllegalStateException actual = assertThrows(IllegalStateException.class, () -> lifecycle.doFilter(
                    servletProxy(HttpServletRequest.class, "failed-session"), servletProxy(HttpServletResponse.class),
                    (request, response) -> fail("filter chain must not run when native session lookup fails")));

            assertAll(
                    () -> assertSame(failure, actual),
                    () -> assertEquals(callerResources, ThreadContext.getResources()),
                    () -> assertEquals(callerShiroResources, org.apache.shiro.util.ThreadContext.getResources()),
                    () -> assertEquals(Collections.singletonList(callerResources), observedFramework),
                    () -> assertEquals(Collections.singletonList(callerShiroResources), observedShiro));
        } finally {
            org.apache.shiro.util.ThreadContext.remove();
            org.apache.shiro.util.ThreadContext.setResources(originalShiroResources);
            callerManager.destroy();
        }
    }
}
