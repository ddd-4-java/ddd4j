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
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    private static SaTokenDao currentDao() {
        return (SaTokenDao) ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "saTokenDao"));
    }

    private static StpInterface currentPermissions() {
        return (StpInterface) ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "stpInterface"));
    }

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

    private Object field(String name) {
        return ReflectUtil.getStaticFieldValue(Objects.requireNonNull(ReflectUtil.getField(SaManager.class, name)));
    }

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

    private static final class TrackingDao extends SaTokenDaoDefaultImpl {
        private int initializations;
        private int destructions;

        @Override
        public void init() {
            initializations++;
        }

        @Override
        public void destroy() {
            destructions++;
            delete("caller-session");
            super.destroy();
        }
    }
}
