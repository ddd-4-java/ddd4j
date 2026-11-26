package io.ddd4j.sample.javalin.satoken;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.listener.SaTokenEventCenter;
import cn.dev33.satoken.listener.SaTokenListenerForSimple;
import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.util.ReflectUtil;
import io.ddd4j.sample.javalin.satoken.NativeAuthHttpTest.Driver;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class NativeSaTokenDaoOwnershipTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    private static Field rawDaoField() {
        Field field = Objects.requireNonNull(ReflectUtil.getField(SaManager.class, "saTokenDao"));
        field.setAccessible(true);
        return field;
    }

    private static void restoreDao(Field field, Object prior) throws IllegalAccessException {
        try {
            if (Objects.nonNull(field.get(null))) {
                SaManager.setSaTokenDao(null);
            }
        } finally {
            field.set(null, prior);
        }
    }

    @Test
    void callerDaoServesLoginAndSurvivesClosingTheApplication() throws Exception {
        Field field = rawDaoField();
        Object prior = field.get(null);
        // Detach the surrounding test runtime's DAO without changing its lifecycle.
        field.set(null, null);
        TrackingDao store = new TrackingDao();
        try {
            SaManager.setSaTokenDao(store);
            store.setObject("caller-owned-key", "preserved", SaTokenDao.NEVER_EXPIRE);
            try (JavalinSaTokenApplication.Application application = JavalinSaTokenApplication.start(0, ignored -> {
            }, config -> {
            }, NativeAuthHttpTest.catalog(MYSQL))) {
                assertEquals(0, store.destroyCalls, "sample start must not shut down the caller's DAO");
                assertSame(store, SaManager.getSaTokenDao());
                Driver http = new Driver(
                        "http://127.0.0.1:" + application.app().port(), Collections.emptyMap());
                Map<String, String> credentials = new HashMap<>();
                credentials.put("username", "admin");
                credentials.put("password", "admin");
                String token = http.request("POST", "/auth/login", credentials, Collections.emptyMap())
                        .path("token").asText();
                assertEquals("10001", http.request("GET", "/auth/me", null,
                        Collections.singletonMap("ddd4j-token", token)).path("loginId").asText());
            }
            assertSame(store, SaManager.getSaTokenDao());
            assertEquals(0, store.destroyCalls, "sample close must not shut down the caller's DAO");
            assertEquals("preserved", store.getObject("caller-owned-key"));
        } finally {
            try {
                SaManager.setSaTokenDao(null);
            } finally {
                field.set(null, prior);
            }
        }
    }

    @Test
    void closingReleasesOwnedDaoWhenRestoringPermissionsAndDetachingDaoReportErrors() throws Exception {
        Field field = rawDaoField();
        Object prior = field.get(null);
        StpInterface previousPermissions = SaManager.getStpInterface();
        RuntimeException restoreFailure = new IllegalStateException("restore permissions callback failed");
        RuntimeException detachFailure = new IllegalStateException("detach DAO callback failed");
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
        field.set(null, null);
        try {
            try (JavalinSaTokenApplication.Application application = JavalinSaTokenApplication.start(0, ignored -> {
            }, config -> {
            }, NativeAuthHttpTest.catalog(MYSQL))) {
                assertNotNull(field.get(null), "the application must install an owned DAO");
                SaTokenEventCenter.registerListener(listener);
                RuntimeException observed = assertThrows(RuntimeException.class, application::close);
                assertSame(restoreFailure, observed);
                assertSame(previousPermissions, SaManager.getStpInterface());
                assertNull(field.get(null), "sample close must detach its owned DAO after a listener failure");
                assertEquals(1, observed.getSuppressed().length);
                assertSame(detachFailure, observed.getSuppressed()[0]);
            }
        } finally {
            SaTokenEventCenter.removeListener(listener);
            restoreDao(field, prior);
        }
    }

    @Test
    void startupPreservesFailureWhenRollbackThrowsTheSameInstance() throws Exception {
        Field field = rawDaoField();
        Object prior = field.get(null);
        StpInterface previousPermissions = SaManager.getStpInterface();
        RuntimeException expected = new IllegalStateException("permission callback failed");
        SaTokenListenerForSimple listener = new SaTokenListenerForSimple() {
            @Override
            public void doRegisterComponent(String name, Object component) {
                if ("StpInterface".equals(name)) {
                    throw expected;
                }
            }
        };
        field.set(null, null);
        SaTokenEventCenter.registerListener(listener);
        try {
            RuntimeException observed = assertThrows(RuntimeException.class,
                    () -> JavalinSaTokenApplication.start(0, ignored -> {
                    }, config -> {
                    }, NativeAuthHttpTest.catalog(MYSQL)).close());
            assertSame(expected, observed, "startup must preserve the original listener failure");
            assertSame(previousPermissions, SaManager.getStpInterface());
            assertNull(field.get(null), "startup rollback must detach its owned DAO");
        } finally {
            SaTokenEventCenter.removeListener(listener);
            restoreDao(field, prior);
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
