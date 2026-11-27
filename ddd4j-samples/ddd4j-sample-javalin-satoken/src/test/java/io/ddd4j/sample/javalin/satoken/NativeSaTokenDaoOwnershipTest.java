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

/** \u9a8c\u8bc1 <code>NativeSaTokenDaoOwnership</code> \u5728 <code>closingReleasesOwnedDao</code>\u3001<code>startupPreservesFailure</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
@Testcontainers
class NativeSaTokenDaoOwnershipTest {
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /**
     * \u8bfb\u53d6 <code>field</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>field</code>
     */
    private static Field rawDaoField() {
        Field field = Objects.requireNonNull(ReflectUtil.getField(SaManager.class, "saTokenDao"));
        field.setAccessible(true);
        return field;
    }

    /**
     * \u8c03\u7528 <code>field.get()</code>\u3001<code>SaManager.setSaTokenDao()</code>\u3001<code>field.set()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param field \u4f20\u7ed9 <code>Objects.nonNull()</code> \u7684 <code>Field</code> \u8f93\u5165
     * @param prior \u4f20\u7ed9 <code>field.set()</code> \u7684 <code>Object</code> \u8f93\u5165
     * @throws IllegalAccessException \u65e0\u6cd5\u8bbf\u95ee\u6240\u9700\u7684\u53cd\u5c04\u6210\u5458
     */
    private static void restoreDao(Field field, Object prior) throws IllegalAccessException {
        try {
            if (Objects.nonNull(field.get(null))) {
                SaManager.setSaTokenDao(null);
            }
        } finally {
            field.set(null, prior);
        }
    }

    /**
     * \u9a8c\u8bc1<code>store.destroyCalls</code> \u7b49\u4e8e <code>0</code>\uff0c<code>SaManager.getSaTokenDao()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>store</code>\uff0c<code>store.getObject("caller-owned-key")</code> \u7b49\u4e8e <code>"preserved"</code>\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>field.get(null)</code>\u975e\u7a7a\uff0c<code>observed.getSuppressed()[0]</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>detachFailure</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>RuntimeException.class</code>\uff0c<code>field.get(null)</code>\u4e3a\u7a7a\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
     */
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
