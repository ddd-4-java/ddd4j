package io.ddd4j.sample.dropwizard.satoken;

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

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全局前任可为 null；关闭不能偷偷创建默认提供器，也不能覆盖后任。
 */
class NativeAuthOwnershipTest {
    /**
     * 调用 <code>ReflectUtil.getStaticFieldValue()</code>、<code>ReflectUtil.getField()</code> 执行当前夹具操作。
     *
     * @return 当前测试使用的结果，取自 <code>(SaTokenDao)ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "saTokenDao"))</code>
     */
    private static SaTokenDao currentDao() {
        return (SaTokenDao) ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "saTokenDao"));
    }

    /**
     * 调用 <code>ReflectUtil.getStaticFieldValue()</code>、<code>ReflectUtil.getField()</code> 执行当前夹具操作。
     *
     * @return 当前测试使用的结果，取自 <code>(StpInterface)ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "stpInterface"))</code>
     */
    private static StpInterface currentPermissions() {
        return (StpInterface) ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "stpInterface"));
    }

    /**
     * 验证<code>SaManager.getSaTokenContext().getModelBox()</code> 与…为不同的实例 <code>before</code>，<code>SaManager.getSaTokenContext().getModelBox()</code> 与…为同一实例 <code>before</code>。
     */
    @Test
    void nestedWorkerScopesRestoreTheExactEnclosingRequest() {
        NativeAuth app = new NativeAuth();
        try (NativeScope outer = new NativeScope(null, null)) {
            Object before = SaManager.getSaTokenContext().getModelBox();
            try (NativeScope inner = new NativeScope(null, null)) {
                org.junit.jupiter.api.Assertions.assertNotSame(before, SaManager.getSaTokenContext().getModelBox());
            }
            assertSame(before, SaManager.getSaTokenContext().getModelBox());
        } finally {
            SaManager.getSaTokenContext().clearContext();
            app.close();
        }
    }

    /**
     * 通过 <code>ReflectUtil.getStaticFieldValue()</code> 取得当前测试需要的结果。
     *
     * @param name 当前测试指定的对象或属性名称
     * @return 当前测试使用的结果，取自 <code>ReflectUtil.getStaticFieldValue(Objects.requireNonNull(ReflectUtil.getField(SaManager.class, name)))</code>
     */
    private Object field(String name) {
        return ReflectUtil.getStaticFieldValue(Objects.requireNonNull(ReflectUtil.getField(SaManager.class, name)));
    }

    /**
     * 验证<code>SaManager.config</code> 与…为同一实例 <code>config</code>，<code>field("stpInterface")</code> 与…为同一实例 <code>permissions</code>，<code>SubjectKit.dataProvider</code> 与…为同一实例 <code>data</code>。
     */
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

    /**
     * 验证<code>SaManager.config</code> 与…为同一实例 <code>replacement</code>，<code>field("stpInterface")</code> 与…为同一实例 <code>null</code>，<code>SubjectKit.dataProvider</code> 与…为同一实例 <code>null</code>。
     */
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

    /**
     * 验证<code>currentDao()</code> 与…为同一实例 <code>caller</code>，<code>caller.get("caller-session")</code> 等于 <code>"kept"</code>，<code>caller.destructions</code> 等于 <code>0</code>。
     */
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

    /**
     * 验证<code>currentDao()</code>为空，被测调用 抛出 <code>IllegalStateException.class</code>，<code>SubjectKit.dataProvider</code> 与…为同一实例 <code>provider</code>。
     */
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

    /**
     * 验证<code>currentDao()</code>为空，被测调用 抛出 <code>IllegalStateException.class</code>，<code>SubjectKit.dataProvider</code> 与…为同一实例 <code>provider</code>。
     */
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

    /**
     * 记录 DAO 操作，供测试验证调用次数或参数。
     */
    private static final class TrackingDao extends SaTokenDaoDefaultImpl {
        /**
         * 记录会话存储执行初始化的次数。
         */
        private int initializations;
        /**
         * 跟踪当前夹具资源被销毁的次数。
         */
        private int destructions;

        /**
         * 记录当前会话存储执行了一次初始化，用于检查重复装配。
         */
        @Override
        public void init() {
            initializations++;
        }

        /**
         * 调用 <code>delete()</code>、<code>super.destroy()</code> 执行当前夹具操作。
         */
        @Override
        public void destroy() {
            destructions++;
            delete("caller-session");
            super.destroy();
        }
    }
}
