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

class SaTokenRuntimeOwnershipTest {

    private static Field daoField() {
        Field field = Objects.requireNonNull(ReflectUtil.getField(SaManager.class, "saTokenDao"));
        ReflectUtil.setAccessible(field);
        return field;
    }

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

    private static final class TrackingDao extends SaTokenDaoDefaultImpl {
        private int destroyCalls;

        @Override
        public void destroy() {
            destroyCalls++;
            super.destroy();
        }
    }
}
