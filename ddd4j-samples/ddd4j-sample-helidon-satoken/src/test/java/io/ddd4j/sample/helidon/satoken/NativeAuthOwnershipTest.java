package io.ddd4j.sample.helidon.satoken;

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

    /** \u9a8c\u8bc1<code>SubjectKit.dataProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>previous</code>\u3002 */
    @Test
    void restoresExactPreviousPermissionProvider() {
        SubjectDataProvider previous = SubjectKit.dataProvider;
        NativeAuth auth = new NativeAuth();
        auth.close();
        assertSame(previous, SubjectKit.dataProvider);
    }

    /** \u9a8c\u8bc1<code>SubjectKit.dataProvider</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>SubjectKit.dataProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>replacement</code>\uff0c<code>SubjectKit.dataProvider</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>currentDao()</code>\u4e3a\u7a7a\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>SubjectKit.dataProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>provider</code>\u3002 */
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
