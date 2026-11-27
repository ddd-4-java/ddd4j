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

/**
 * 验证 <code>SaTokenRuntimeOwnership</code> 在 <code>borrowedNativeStoreSurvivesClose</code>、<code>shutdownDetachesOwnedDao</code> 等场景中的行为。
 */
class SaTokenRuntimeOwnershipTest {

    /**
     * 读取 <code>field</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>field</code>
     */
    private static Field daoField() {
        Field field = Objects.requireNonNull(ReflectUtil.getField(SaManager.class, "saTokenDao"));
        ReflectUtil.setAccessible(field);
        return field;
    }

    /**
     * 验证<code>callerDao.destroyCalls</code> 等于 <code>0</code>，<code>daoField.get(null)</code> 与…为同一实例 <code>callerDao</code>，<code>callerDao.getObject("caller-owned-key")</code> 等于 <code>"preserved"</code>。
     *
     * @throws IllegalAccessException 无法访问所需的反射成员
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
     * 验证<code>daoField.get(null)</code>非空，<code>daoField.get(null)</code>为空，<code>replacement.destroyCalls</code> 等于 <code>0</code>。
     *
     * @throws IllegalAccessException 无法访问所需的反射成员
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
     * 验证<code>runtime.start(null)</code> 抛出 <code>RuntimeException.class</code>，<code>daoField.get(null)</code>为空。
     *
     * @throws IllegalAccessException 无法访问所需的反射成员
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
     * 验证<code>runtime.stop(null)</code> 抛出 <code>RuntimeException.class</code>，<code>failure</code> 与…为同一实例 <code>restoreFailure</code>，<code>failure.getSuppressed()[0]</code> 与…为同一实例 <code>detachFailure</code>。
     *
     * @throws IllegalAccessException 无法访问所需的反射成员
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

    /**
     * 记录 DAO 操作，供测试验证调用次数或参数。
     */
    private static final class TrackingDao extends SaTokenDaoDefaultImpl {
        /**
         * 跟踪应用或代理资源执行销毁操作的次数。
         */
        private int destroyCalls;

        /**
         * 调用 <code>super.destroy()</code> 执行当前夹具操作。
         */
        @Override
        public void destroy() {
            destroyCalls++;
            super.destroy();
        }
    }
}
