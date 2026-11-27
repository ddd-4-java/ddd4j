package io.ddd4j.sample.micronaut.satoken;

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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 精确恢复可空前任权限源，且保留应用后来安装或清空的权限源。
 */
class NativeAuthOwnershipTest {
    private static SaTokenDao currentDao() {
        return (SaTokenDao) ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "saTokenDao"));
    }

    private static StpInterface currentPermissions() {
        return (StpInterface) ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "stpInterface"));
    }

    @Test
    void restoresRawNullPreviousProvider() {
        SubjectDataProvider previous = SubjectKit.dataProvider;
        try {
            SubjectKit.setDataProvider(null);
            new NativeAuth().close();
            assertNull(SubjectKit.dataProvider);
        } finally {
            SubjectKit.setDataProvider(previous);
        }
    }

    @Test
    void preservesReplacementAndExplicitClear() {
        SubjectDataProvider previous = SubjectKit.dataProvider;
        try {
            NativeAuth auth = new NativeAuth();
            SubjectDataProvider replacement = new SubjectDataProvider() {
            };
            SubjectKit.setDataProvider(replacement);
            auth.close();
            assertSame(replacement, SubjectKit.dataProvider);
            auth = new NativeAuth();
            SubjectKit.setDataProvider(null);
            auth.close();
            assertNull(SubjectKit.dataProvider);
        } finally {
            SubjectKit.setDataProvider(previous);
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
            assertSame(permissions, currentPermissions());
            assertSame(provider, SubjectKit.dataProvider);
        } finally {
            SaTokenEventCenter.removeListener(listener);
            SaManager.setSaTokenDao(null);
            SaManager.setConfig(config);
            SaManager.setStpInterface(permissions);
            SubjectKit.setDataProvider(provider);
        }
    }

    @Test
    void completesCleanupWhenPermissionListenerFailsOnClose() {
        assertNull(currentDao());
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
            assertSame(permissions, currentPermissions());
            assertSame(provider, SubjectKit.dataProvider);
        } finally {
            SaTokenEventCenter.removeListener(listener);
            auth.close();
            SaManager.setSaTokenDao(null);
            SaManager.setConfig(config);
            SaManager.setStpInterface(permissions);
            SubjectKit.setDataProvider(provider);
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
