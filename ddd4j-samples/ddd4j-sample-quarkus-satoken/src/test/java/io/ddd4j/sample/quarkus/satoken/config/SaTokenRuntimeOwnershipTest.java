package io.ddd4j.sample.quarkus.satoken.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.listener.SaTokenEventCenter;
import cn.dev33.satoken.listener.SaTokenListenerForSimple;
import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.util.ReflectUtil;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>SaTokenRuntimeOwnership</code> \u5728 <code>borrowedNativeStoreSurvivesClose</code>\u3001<code>shutdownDetachesOwnedDao</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class SaTokenRuntimeOwnershipTest {

    /**
     * \u8bfb\u53d6 <code>field</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>field</code>
     */
    private static Field daoField() {
        Field field = Objects.requireNonNull(ReflectUtil.getField(SaManager.class, "saTokenDao"));
        ReflectUtil.setAccessible(field);
        return field;
    }

    /**
     * \u9a8c\u8bc1<code>callerDao.destroyCalls</code> \u7b49\u4e8e <code>0</code>\uff0c<code>daoField.get(null)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>callerDao</code>\uff0c<code>callerDao.getObject("caller-owned-key")</code> \u7b49\u4e8e <code>"preserved"</code>\u3002
     * @throws IllegalAccessException \u65e0\u6cd5\u8bbf\u95ee\u6240\u9700\u7684\u53cd\u5c04\u6210\u5458
     */
    @Test
    void borrowedNativeStoreSurvivesCloseWithItsData() throws IllegalAccessException {
        Field daoField = daoField();
        Object originalDao = daoField.get(null);
        SaTokenConfig originalConfig = SaManager.getConfig();
        TrackingDao callerDao = new TrackingDao();
        SaTokenRuntimeConfig runtime = new SaTokenRuntimeConfig();
        try {
            daoField.set(null, null);
            SaManager.setConfig(new SaTokenConfig().setIsPrint(false).setDataRefreshPeriod(-1));
            SaManager.setSaTokenDao(callerDao);
            callerDao.setObject("caller-owned-key", "preserved", SaTokenDao.NEVER_EXPIRE);

            runtime.start(null);
            assertEquals(0, callerDao.destroyCalls, "sample startup must keep the caller's DAO usable");
            assertSame(callerDao, daoField.get(null));

            runtime.stop(null);
            assertSame(callerDao, daoField.get(null));
            assertEquals(0, callerDao.destroyCalls);
            assertEquals("preserved", callerDao.getObject("caller-owned-key"));
        } finally {
            runtime.destroy();
            daoField.set(null, originalDao);
            SaManager.setConfig(originalConfig);
            callerDao.destroy();
        }
    }

    /**
     * \u9a8c\u8bc1<code>daoField.get(null)</code>\u975e\u7a7a\uff0c<code>daoField.get(null)</code>\u4e3a\u7a7a\uff0c<code>replacement.destroyCalls</code> \u7b49\u4e8e <code>0</code>\u3002
     * @throws IllegalAccessException \u65e0\u6cd5\u8bbf\u95ee\u6240\u9700\u7684\u53cd\u5c04\u6210\u5458
     */
    @Test
    void ownedDaoRestoresEmptySlotAndPreservesAReplacement() throws IllegalAccessException {
        Field daoField = daoField();
        Object originalDao = daoField.get(null);
        SaTokenConfig originalConfig = SaManager.getConfig();
        SaTokenConfig callerConfig = new SaTokenConfig().setIsPrint(false).setDataRefreshPeriod(-1);
        TrackingDao replacement = new TrackingDao();
        SaTokenRuntimeConfig runtime = new SaTokenRuntimeConfig();
        try {
            daoField.set(null, null);
            SaManager.setConfig(callerConfig);
            runtime.start(null);
            assertNotNull(daoField.get(null));
            runtime.stop(null);
            assertNull(daoField.get(null));
            assertSame(callerConfig, SaManager.getConfig());

            runtime.start(null);
            SaManager.setSaTokenDao(replacement);
            SaManager.setConfig(callerConfig);
            runtime.stop(null);
            assertSame(replacement, daoField.get(null));
            assertSame(callerConfig, SaManager.getConfig());
            assertEquals(0, replacement.destroyCalls);
        } finally {
            runtime.destroy();
            daoField.set(null, originalDao);
            SaManager.setConfig(originalConfig);
            replacement.destroy();
        }
    }

    /**
     * \u9a8c\u8bc1<code>runtime.start(null)</code> \u629b\u51fa <code>RuntimeException.class</code>\uff0c<code>daoField.get(null)</code>\u4e3a\u7a7a\u3002
     * @throws IllegalAccessException \u65e0\u6cd5\u8bbf\u95ee\u6240\u9700\u7684\u53cd\u5c04\u6210\u5458
     */
    @Test
    void failedDaoRegistrationRestoresAnEmptySlot() throws IllegalAccessException {
        Field daoField = daoField();
        Object originalDao = daoField.get(null);
        RuntimeException rejected = new IllegalStateException("DAO registration failed");
        SaTokenListenerForSimple listener = new SaTokenListenerForSimple() {
            @Override
            public void doRegisterComponent(String name, Object component) {
                if ("SaTokenDao".equals(name) && Objects.nonNull(component)) {
                    throw rejected;
                }
            }
        };
        SaTokenRuntimeConfig runtime = new SaTokenRuntimeConfig();
        daoField.set(null, null);
        SaTokenEventCenter.registerListener(listener);
        try {
            assertSame(rejected, assertThrows(RuntimeException.class, () -> runtime.start(null)));
            assertNull(daoField.get(null), "failed startup must detach the DAO it registered");
        } finally {
            SaTokenEventCenter.removeListener(listener);
            try {
                runtime.destroy();
            } finally {
                daoField.set(null, originalDao);
            }
        }
    }

    /**
     * \u9a8c\u8bc1<code>runtime.stop(null)</code> \u629b\u51fa <code>RuntimeException.class</code>\uff0c<code>failure</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>restoreFailure</code>\uff0c<code>failure.getSuppressed()[0]</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>detachFailure</code>\u3002
     * @throws IllegalAccessException \u65e0\u6cd5\u8bbf\u95ee\u6240\u9700\u7684\u53cd\u5c04\u6210\u5458
     */
    @Test
    void shutdownDetachesOwnedDaoWhenRestoringPermissionsThrows() throws IllegalAccessException {
        Field daoField = daoField();
        Object originalDao = daoField.get(null);
        SaTokenConfig originalConfig = SaManager.config;
        StpInterface previousPermissions = SaManager.getStpInterface();
        RuntimeException restoreFailure = new IllegalStateException("permission restoration failed");
        RuntimeException detachFailure = new IllegalStateException("DAO detach notification failed");
        SaTokenListenerForSimple listener = new SaTokenListenerForSimple() {
            @Override
            public void doRegisterComponent(String name, Object component) {
                if ("StpInterface".equals(name) && component == previousPermissions) {
                    throw restoreFailure;
                }
                if ("SaTokenDao".equals(name) && Objects.isNull(component)) {
                    throw detachFailure;
                }
            }
        };
        SaTokenRuntimeConfig runtime = new SaTokenRuntimeConfig();
        daoField.set(null, null);
        try {
            runtime.start(null);
            SaTokenEventCenter.registerListener(listener);

            RuntimeException failure = assertThrows(RuntimeException.class, () -> runtime.stop(null));
            assertSame(restoreFailure, failure);
            assertSame(previousPermissions, SaManager.getStpInterface());
            assertNull(daoField.get(null), "shutdown must detach the owned DAO even when permissions fail to restore");
            assertSame(originalConfig, SaManager.config);
            assertEquals(1, failure.getSuppressed().length);
            assertSame(detachFailure, failure.getSuppressed()[0]);
        } finally {
            SaTokenEventCenter.removeListener(listener);
            try {
                runtime.destroy();
            } finally {
                daoField.set(null, originalDao);
                SaManager.setConfig(originalConfig);
                SaManager.setStpInterface(previousPermissions);
            }
        }
    }

    /** \u8bb0\u5f55 DAO \u64cd\u4f5c\uff0c\u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u8c03\u7528\u6b21\u6570\u6216\u53c2\u6570\u3002 */
    private static final class TrackingDao extends SaTokenDaoDefaultImpl {
        /** \u8ddf\u8e2a\u5e94\u7528\u6216\u4ee3\u7406\u8d44\u6e90\u6267\u884c\u9500\u6bc1\u64cd\u4f5c\u7684\u6b21\u6570\u3002 */
        private int destroyCalls;

        /** \u8c03\u7528 <code>super.destroy()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
        @Override
        public void destroy() {
            destroyCalls++;
            super.destroy();
        }
    }
}
