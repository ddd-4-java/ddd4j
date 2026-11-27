package io.ddd4j.sample.vertx.satoken;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.listener.SaTokenEventCenter;
import cn.dev33.satoken.listener.SaTokenListenerForSimple;
import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.util.ReflectUtil;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.util.SubjectKit;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全局前任可为 null；关闭不能偷偷创建默认提供器，也不能覆盖后任。
 */
@Testcontainers
class NativeAuthOwnershipTest {
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /**
     * \u8c03\u7528 <code>ReflectUtil.getStaticFieldValue()</code>\u3001<code>ReflectUtil.getField()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>(SaTokenDao)ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "saTokenDao"))</code>
     */
    private static SaTokenDao currentDao() {
        return (SaTokenDao) ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "saTokenDao"));
    }

    /**
     * \u8c03\u7528 <code>ReflectUtil.getStaticFieldValue()</code>\u3001<code>ReflectUtil.getField()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>(StpInterface)ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "stpInterface"))</code>
     */
    private static StpInterface currentPermissions() {
        return (StpInterface) ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "stpInterface"));
    }

    /** \u9a8c\u8bc1<code>SaManager.getSaTokenContext().getModelBox()</code> \u4e0e\u2026\u4e3a\u4e0d\u540c\u7684\u5b9e\u4f8b <code>before</code>\uff0c<code>SaManager.getSaTokenContext().getModelBox()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>before</code>\u3002 */
    @Test
    void nestedWorkerScopesRestoreTheExactEnclosingRequest() {
        NativeAuth app = new NativeAuth();
        try (NativeScope outer = new NativeScope(null)) {
            Object before = SaManager.getSaTokenContext().getModelBox();
            try (NativeScope inner = new NativeScope(null)) {
                org.junit.jupiter.api.Assertions.assertNotSame(before, SaManager.getSaTokenContext().getModelBox());
            }
            assertSame(before, SaManager.getSaTokenContext().getModelBox());
        } finally {
            SaManager.getSaTokenContext().clearContext();
            app.close();
        }
    }

    /**
     * \u901a\u8fc7 <code>ReflectUtil.getStaticFieldValue()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>ReflectUtil.getStaticFieldValue(Objects.requireNonNull(ReflectUtil.getField(SaManager.class, name)))</code>
     */
    private Object field(String name) {
        return ReflectUtil.getStaticFieldValue(Objects.requireNonNull(ReflectUtil.getField(SaManager.class, name)));
    }

    /** \u9a8c\u8bc1<code>SaManager.config</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>config</code>\uff0c<code>field("stpInterface")</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>permissions</code>\uff0c<code>SubjectKit.dataProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>data</code>\u3002 */
    @Test
    void restoresExactNullableOwners() {
        var config = SaManager.config;
        var permissions = field("stpInterface");
        var context = field("saTokenContext");
        var dao = field("saTokenDao");
        var data = SubjectKit.dataProvider;
        var auth = new NativeAuth();
        auth.close();
        assertSame(config, SaManager.config);
        assertSame(permissions, field("stpInterface"));
        assertSame(context, field("saTokenContext"));
        assertSame(dao, field("saTokenDao"));
        assertSame(data, SubjectKit.dataProvider);
    }

    /** \u9a8c\u8bc1<code>SaManager.config</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>replacement</code>\uff0c<code>field("stpInterface")</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>null</code>\uff0c<code>SubjectKit.dataProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>null</code>\u3002 */
    @Test
    void preservesReplacementOwner() {
        var originalConfig = SaManager.config;
        var originalData = SubjectKit.dataProvider;
        var auth = new NativeAuth();
        var replacement = new cn.dev33.satoken.config.SaTokenConfig().setIsPrint(false);
        SaManager.setConfig(replacement);
        SaManager.setStpInterface(null);
        SaManager.setSaTokenContext(null);
        SubjectKit.dataProvider = null;
        try {
            auth.close();
            assertSame(replacement, SaManager.config);
            assertSame(null, field("stpInterface"));
            assertSame(null, field("saTokenContext"));
            assertSame(null, SubjectKit.dataProvider);
        } finally {
            SaManager.config = originalConfig;
            SubjectKit.dataProvider = originalData;
        }
    }

    /** \u9a8c\u8bc1<code>currentDao()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>caller</code>\uff0c<code>caller.get("caller-session")</code> \u7b49\u4e8e <code>"kept"</code>\uff0c<code>caller.destructions</code> \u7b49\u4e8e <code>0</code>\u3002 */
    @Test
    void preservesCallerDaoAndItsData() {
        SaTokenDao previous = currentDao();
        TrackingDao caller = new TrackingDao();
        ReflectUtil.setFieldValue(SaManager.class, ReflectUtil.getField(SaManager.class, "saTokenDao"), caller);
        caller.set("caller-session", "kept", SaTokenDao.NEVER_EXPIRE);
        try {
            NativeAuth auth = new NativeAuth();
            try {
                assertSame(caller, currentDao());
                assertEquals("kept", caller.get("caller-session"));
            } finally {
                auth.close();
            }
            assertSame(caller, currentDao());
            assertEquals(0, caller.initializations);
            assertEquals(0, caller.destructions);
            assertEquals("kept", caller.get("caller-session"));
        } finally {
            ReflectUtil.setFieldValue(SaManager.class, ReflectUtil.getField(SaManager.class, "saTokenDao"), previous);
            caller.destroy();
        }
    }

    /** \u9a8c\u8bc1<code>currentDao()</code>\u4e3a\u7a7a\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>SubjectKit.dataProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>provider</code>\u3002 */
    @Test
    void rollsBackWhenNativeDaoRegistrationFails() {
        assertNull(currentDao());
        Object context = field("saTokenContext");
        SaTokenConfig config = SaManager.config;
        StpInterface permissions = currentPermissions();
        SubjectDataProvider provider = SubjectKit.dataProvider;
        IllegalStateException failure = new IllegalStateException("native DAO registration failed");
        SaTokenListenerForSimple listener = new SaTokenListenerForSimple() {
            @Override
            public void doRegisterComponent(String name, Object registered) {
                if ("SaTokenDao".equals(name) && registered instanceof SaTokenDaoDefaultImpl) {
                    throw failure;
                }
            }
        };
        SaTokenEventCenter.registerListener(listener);
        try {
            assertSame(failure, assertThrows(IllegalStateException.class, NativeAuth::new));
            assertNull(currentDao());
            assertSame(config, SaManager.config);
            assertSame(context, field("saTokenContext"));
            assertSame(permissions, currentPermissions());
            assertSame(provider, SubjectKit.dataProvider);
        } finally {
            SaTokenEventCenter.removeListener(listener);
            SaManager.setSaTokenDao(null);
            SaManager.config = config;
            SaManager.setStpInterface(permissions);
            SubjectKit.setDataProvider(provider);
        }
    }

    /** \u9a8c\u8bc1<code>currentDao()</code>\u4e3a\u7a7a\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>SubjectKit.dataProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>provider</code>\u3002 */
    @Test
    void completesCleanupWhenPermissionListenerFailsOnClose() {
        assertNull(currentDao());
        Object context = field("saTokenContext");
        SaTokenConfig config = SaManager.config;
        StpInterface permissions = currentPermissions();
        SubjectDataProvider provider = SubjectKit.dataProvider;
        NativeAuth auth = new NativeAuth();
        IllegalStateException failure = new IllegalStateException("native permission restoration failed");
        SaTokenListenerForSimple listener = new SaTokenListenerForSimple() {
            @Override
            public void doRegisterComponent(String name, Object registered) {
                if ("StpInterface".equals(name)) {
                    throw failure;
                }
            }
        };
        SaTokenEventCenter.registerListener(listener);
        try {
            assertSame(failure, assertThrows(IllegalStateException.class, auth::close));
            assertNull(currentDao());
            assertSame(config, SaManager.config);
            assertSame(context, field("saTokenContext"));
            assertSame(permissions, currentPermissions());
            assertSame(provider, SubjectKit.dataProvider);
        } finally {
            SaTokenEventCenter.removeListener(listener);
            auth.close();
            SaManager.setSaTokenDao(null);
            SaManager.config = config;
            SaManager.setStpInterface(permissions);
            SubjectKit.setDataProvider(provider);
        }
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>java.util.concurrent.ExecutionException.class</code>\uff0c<code>actual.getCause()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>failure</code>\uff0c<code>SubjectKit.subjectProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>previous</code>\u3002 */
    @Test
    void appRestoresRegistrationsAfterNativeStartupFailure() {
        Object previous = SubjectKit.subjectProvider;
        IllegalStateException failure = new IllegalStateException("sample startup listener");
        SaTokenListenerForSimple listener = new SaTokenListenerForSimple() {
            @Override
            public void doRegisterComponent(String name, Object registered) {
                if ("StpInterface".equals(name) && Objects.nonNull(registered)) {
                    throw failure;
                }
            }
        };
        SaTokenEventCenter.registerListener(listener);
        try {
            java.util.concurrent.ExecutionException actual = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> VertxSaTokenApplication.start(0, NativeAuthHttpTest.catalog(MYSQL), (id, clean) -> {
                            }).toCompletionStage().toCompletableFuture()
                            .get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertSame(failure, actual.getCause());
            assertSame(previous, SubjectKit.subjectProvider);
        } finally {
            SaTokenEventCenter.removeListener(listener);
        }
    }

    /**
     * \u9a8c\u8bc1<code>app.close().toCompletionStage().toCompletableFuture().get(15, java.util.concurrent.TimeUnit.SECONDS)</code> \u629b\u51fa <code>java.util.concurrent.ExecutionException.class</code>\uff0c<code>actual.getCause()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>failure</code>\uff0c<code>SubjectKit.subjectProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>previous</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void appRestoresRegistrationsAfterNativeShutdownFailure() throws Exception {
        Object previous = SubjectKit.subjectProvider;
        VertxSaTokenApplication app = VertxSaTokenApplication.start(0, NativeAuthHttpTest.catalog(MYSQL), (id, clean) -> {
                }).toCompletionStage().toCompletableFuture()
                .get(15, java.util.concurrent.TimeUnit.SECONDS);
        IllegalStateException failure = new IllegalStateException("sample shutdown listener");
        SaTokenListenerForSimple listener = new SaTokenListenerForSimple() {
            @Override
            public void doRegisterComponent(String name, Object registered) {
                if ("StpInterface".equals(name)) {
                    throw failure;
                }
            }
        };
        SaTokenEventCenter.registerListener(listener);
        try {
            java.util.concurrent.ExecutionException actual = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> app.close().toCompletionStage().toCompletableFuture().get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertSame(failure, actual.getCause());
            assertSame(previous, SubjectKit.subjectProvider);
        } finally {
            SaTokenEventCenter.removeListener(listener);
        }
    }

    /** \u8bb0\u5f55 DAO \u64cd\u4f5c\uff0c\u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u8c03\u7528\u6b21\u6570\u6216\u53c2\u6570\u3002 */
    private static final class TrackingDao extends SaTokenDaoDefaultImpl {
        /** \u8bb0\u5f55\u4f1a\u8bdd\u5b58\u50a8\u6267\u884c\u521d\u59cb\u5316\u7684\u6b21\u6570\u3002 */
        private int initializations;
        /** \u8ddf\u8e2a\u5f53\u524d\u5939\u5177\u8d44\u6e90\u88ab\u9500\u6bc1\u7684\u6b21\u6570\u3002 */
        private int destructions;

        /** \u8bb0\u5f55\u5f53\u524d\u4f1a\u8bdd\u5b58\u50a8\u6267\u884c\u4e86\u4e00\u6b21\u521d\u59cb\u5316\uff0c\u7528\u4e8e\u68c0\u67e5\u91cd\u590d\u88c5\u914d\u3002 */
        @Override
        public void init() {
            initializations++;
        }

        /** \u8c03\u7528 <code>delete()</code>\u3001<code>super.destroy()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
        @Override
        public void destroy() {
            destructions++;
            delete("caller-session");
            super.destroy();
        }
    }
}
