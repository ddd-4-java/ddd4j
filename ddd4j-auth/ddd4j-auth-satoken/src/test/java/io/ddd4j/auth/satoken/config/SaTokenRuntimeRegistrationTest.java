package io.ddd4j.auth.satoken.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.json.SaJsonTemplate;
import cn.dev33.satoken.json.SaJsonTemplateDefaultImpl;
import cn.dev33.satoken.listener.SaTokenEventCenter;
import cn.dev33.satoken.listener.SaTokenListenerForSimple;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpInterfaceDefaultImpl;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ReflectUtil;
import io.ddd4j.auth.satoken.subject.SaTokenSubjectDataBridge;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.util.SubjectKit;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>SaTokenRuntimeRegistration</code> \u5728<code>emptyDaoSlotIsRestored</code>\u3001<code>sharingTheSameJsonTemplateKeepsItsOwnerLive</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class SaTokenRuntimeRegistrationTest {

    /**
     * \u521b\u5efa <code>new ProbeDao</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param failure \u89e6\u53d1\u5f53\u524d\u6d4b\u8bd5\u9519\u8bef\u5904\u7406\u8def\u5f84\u7684\u5931\u8d25
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new ProbeDao(){ &#64;Override public void init() { super.init(); if (inits == 2) { throw failure; } } }</code>
     */
    private static ProbeDao failsFirstRestoration(IllegalStateException failure) {
        return new ProbeDao() {
            @Override
            public void init() {
                super.init();
                if (inits == 2) {
                    throw failure;
                }
            }
        };
    }

    /**
     * \u8c03\u7528 <code>ReflectUtil.getStaticFieldValue()</code>\u3001<code>ReflectUtil.getField()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>(SaTokenDao)ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "saTokenDao"))</code>
     */
    private static SaTokenDao currentDao() {
        return (SaTokenDao) ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "saTokenDao"));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\uff0c<code>SaManager.getConfig()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>previousConfig</code>\uff0c<code>config.getTokenName()</code> \u7b49\u4e8e <code>"native"</code>\u3002 */
    @Test
    void sessionConfigOverridesDefaultsAndRestoresOwnedComponents() {
        SaTokenConfig previousConfig = SaManager.getConfig();
        StpLogic previousLogic = StpUtil.stpLogic;
        SaTokenContext previousContext = SaManager.getSaTokenContext();
        StpInterface previousPermissions = SaManager.getStpInterface();
        SubjectDataProvider previousData = SubjectKit.dataProvider;
        SaTokenConfig config = new SaTokenConfig().setTokenName("native").setTokenPrefix("Bearer").setIsPrint(false);
        SaTokenContext context = new SaTokenContextForThreadLocal();
        SubjectDataProvider provider = new SubjectDataProvider() {
        };
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setLoginType("runtime-test");
        try {
            SaManager.setStpInterface(new StpInterfaceDefaultImpl());
            try (SaTokenRuntimeRegistration runtime = SaTokenRuntimeRegistration.builder()
                    .context(context).nativeConfig(config).security(properties).subjectData(provider).build()) {
                properties.setTokenNamespace("invalid.space");
                assertThrows(IllegalArgumentException.class, runtime::start);
                assertSame(previousConfig, SaManager.getConfig());
                assertSame(previousLogic, StpUtil.stpLogic);

                properties.setTokenNamespace("runtime");
                runtime.start();
                assertSame(context, SaManager.getSaTokenContext());
                assertSame(provider, SubjectKit.dataProvider);
                assertInstanceOf(SaTokenSubjectDataBridge.class, SaManager.getStpInterface());
                assertEquals("runtime-token", SaManager.getConfig().getTokenName());
                assertEquals("Bearer", SaManager.getConfig().getTokenPrefix());
                assertSame(StpUtil.stpLogic, SaManager.stpLogicMap.get("runtime-test"));
            }
            assertSame(previousContext, SaManager.getSaTokenContext());
            assertSame(previousConfig, SaManager.getConfig());
            assertSame(previousData, SubjectKit.dataProvider);
            assertSame(previousLogic, StpUtil.stpLogic);
            assertNull(SaManager.stpLogicMap.get("runtime-test"));
            assertEquals("native", config.getTokenName());
            assertInstanceOf(StpInterfaceDefaultImpl.class, SaManager.getStpInterface());
        } finally {
            SaManager.setStpInterface(previousPermissions);
        }
    }

    /** \u9a8c\u8bc1<code>StpUtil.stpLogic</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>logic</code>\uff0c<code>SaManager.getStpInterface()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>permissions</code>\uff0c<code>SaManager.getStpInterface()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>previousPermissions</code>\u3002 */
    @Test
    void customLogicAndPermissionSourceTakePrecedence() {
        StpLogic previousLogic = StpUtil.stpLogic;
        StpInterface previousPermissions = SaManager.getStpInterface();
        StpLogic logic = new StpLogic("custom-runtime-test");
        StpInterface permissions = new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return Collections.singletonList("custom");
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return Collections.emptyList();
            }
        };
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);

        try {
            try (SaTokenRuntimeRegistration runtime = SaTokenRuntimeRegistration.builder()
                    .security(properties).customLogic(logic).customPermissions(permissions).build()) {
                runtime.start();
                assertSame(logic, StpUtil.stpLogic);
                assertSame(permissions, SaManager.getStpInterface());
            }
            assertSame(previousLogic, StpUtil.stpLogic);
            assertSame(previousPermissions, SaManager.getStpInterface());
        } finally {
            SaManager.removeStpLogic(logic.getLoginType());
        }
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>StpUtil.stpLogic</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>shared</code>\uff0c<code>publications.get()</code> \u7b49\u4e8e <code>2</code>\u3002 */
    @Test
    void sharingTheSameCustomLogicKeepsItsOwnerLiveAndPreservesNativeNotifications() {
        String realm = "runtime-shared-logic";
        StpLogic defaultLogic = StpUtil.stpLogic;
        StpLogic shared = new StpLogic(realm);
        StpLogic previous = new StpLogic(realm);
        SaTokenRuntimeRegistration owner = SaTokenRuntimeRegistration.builder().customLogic(shared).build();
        SaTokenRuntimeRegistration borrower = SaTokenRuntimeRegistration.builder().customLogic(shared).build();
        AtomicInteger publications = new AtomicInteger();
        SaTokenListenerForSimple listener = new SaTokenListenerForSimple() {
            @Override
            public void doSetStpLogic(StpLogic installed) {
                if (installed == shared) {
                    publications.incrementAndGet();
                }
            }
        };
        SaTokenEventCenter.registerListener(listener);
        try {
            owner.start();
            borrower.start();
            assertThrows(IllegalStateException.class, owner::close);
            assertSame(shared, StpUtil.stpLogic);
            assertSame(shared, SaManager.stpLogicMap.get(realm));
            borrower.close();
            owner.close();
            assertSame(defaultLogic, StpUtil.stpLogic);
            assertSame(previous, SaManager.stpLogicMap.get(realm));
            assertEquals(2, publications.get());
        } finally {
            borrower.close();
            owner.close();
            SaTokenEventCenter.removeListener(listener);
            SaManager.removeStpLogic(realm);
        }
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>SaManager.stpLogicMap.get(realm)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>shared</code>\uff0c<code>SaManager.stpLogicMap.get(realm)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>externalRealm</code>\u3002 */
    @Test
    void replacingTheDefaultLogicLeavesTheOtherBorrowedRealmProtected() {
        String realm = "runtime-shared-realm";
        String defaultRealm = "runtime-external-default";
        StpLogic defaultLogic = StpUtil.stpLogic;
        StpLogic shared = new StpLogic(realm);
        StpLogic externalRealm = new StpLogic(realm);
        StpLogic externalDefault = new StpLogic(defaultRealm);
        SaTokenRuntimeRegistration owner = SaTokenRuntimeRegistration.builder().customLogic(shared).build();
        SaTokenRuntimeRegistration borrower = SaTokenRuntimeRegistration.builder().customLogic(shared).build();
        try {
            owner.start();
            borrower.start();
            StpUtil.setStpLogic(externalDefault);
            assertThrows(IllegalStateException.class, owner::close);
            assertSame(shared, SaManager.stpLogicMap.get(realm));
            SaManager.putStpLogic(externalRealm);
            owner.close();
            borrower.close();
            assertSame(externalDefault, StpUtil.stpLogic);
            assertSame(externalRealm, SaManager.stpLogicMap.get(realm));
        } finally {
            borrower.close();
            owner.close();
            StpUtil.setStpLogic(defaultLogic);
            SaManager.removeStpLogic(realm);
            SaManager.removeStpLogic(defaultRealm);
        }
    }

    /** \u9a8c\u8bc1<code>SaManager.stpLogicMap.get(realm)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>previous</code>\uff0c<code>StpUtil.stpLogic</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>expected</code>\uff0c<code>StpUtil.stpLogic</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>defaultLogic</code>\u3002 */
    @Test
    void customLogicLeavesTheRealmInstalledByAnSdkListener() {
        String realm = "runtime-custom-callback";
        StpLogic defaultLogic = StpUtil.stpLogic;
        StpLogic expected = new StpLogic(realm);
        StpLogic external = new StpLogic(realm);
        StpLogic previous = new StpLogic(realm);
        SaTokenListenerForSimple listener = new SaTokenListenerForSimple() {
            @Override
            public void doSetStpLogic(StpLogic installed) {
                if (installed == expected) {
                    SaManager.putStpLogic(external);
                }
            }
        };
        SaTokenEventCenter.registerListener(listener);
        try {
            assertSame(previous, SaManager.stpLogicMap.get(realm));
            try (SaTokenRuntimeRegistration runtime = SaTokenRuntimeRegistration.builder().customLogic(expected).build()) {
                runtime.start();
                assertSame(expected, StpUtil.stpLogic);
                assertSame(external, SaManager.stpLogicMap.get(realm));
            }
            assertSame(defaultLogic, StpUtil.stpLogic);
            assertSame(external, SaManager.stpLogicMap.get(realm));
        } finally {
            SaTokenEventCenter.removeListener(listener);
            SaManager.removeStpLogic(realm);
        }
    }

    /** \u9a8c\u8bc1<code>SaManager.stpLogicMap.get(realm)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>previous</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>SaManager.stpLogicMap.get(realm)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>external</code>\u3002 */
    @Test
    void securityStartFailureLeavesTheRealmInstalledByAnSdkListener() {
        String realm = "runtime-security-callback";
        StpLogic defaultLogic = StpUtil.stpLogic;
        StpLogic external = new StpLogic(realm);
        StpLogic previous = new StpLogic(realm);
        SaTokenConfig originalConfig = SaManager.getConfig();
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setLoginType(realm);
        IllegalStateException failure = new IllegalStateException("登录域监听器失败");
        SaTokenListenerForSimple listener = new SaTokenListenerForSimple() {
            @Override
            public void doSetStpLogic(StpLogic installed) {
                if (realm.equals(installed.getLoginType())) {
                    SaManager.putStpLogic(external);
                    throw failure;
                }
            }
        };
        SaTokenEventCenter.registerListener(listener);
        try {
            assertSame(previous, SaManager.stpLogicMap.get(realm));
            try (SaTokenRuntimeRegistration runtime = SaTokenRuntimeRegistration.builder()
                    .nativeConfig(new SaTokenConfig().setIsPrint(false)).security(properties).build()) {
                assertSame(failure, assertThrows(IllegalStateException.class, runtime::start));
                assertSame(defaultLogic, StpUtil.stpLogic);
                assertSame(originalConfig, SaManager.getConfig());
                assertSame(external, SaManager.stpLogicMap.get(realm));
            }
        } finally {
            SaTokenEventCenter.removeListener(listener);
            SaManager.removeStpLogic(realm);
        }
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>StpUtil.stpLogic</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>defaultLogic</code>\uff0c<code>SaManager.stpLogicMap.get(realm)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>previous</code>\u3002 */
    @Test
    void failureInAnSdkListenerRestoresTheOwnedCustomLogic() {
        String realm = "runtime-owned-callback";
        StpLogic defaultLogic = StpUtil.stpLogic;
        StpLogic expected = new StpLogic(realm);
        StpLogic previous = new StpLogic(realm);
        IllegalStateException failure = new IllegalStateException("登录域监听器失败");
        SaTokenListenerForSimple listener = new SaTokenListenerForSimple() {
            @Override
            public void doSetStpLogic(StpLogic installed) {
                if (installed == expected) {
                    throw failure;
                }
            }
        };
        SaTokenEventCenter.registerListener(listener);
        try {
            try (SaTokenRuntimeRegistration runtime = SaTokenRuntimeRegistration.builder().customLogic(expected).build()) {
                assertSame(failure, assertThrows(IllegalStateException.class, runtime::start));
                assertSame(defaultLogic, StpUtil.stpLogic);
                assertSame(previous, SaManager.stpLogicMap.get(realm));
            }
        } finally {
            SaTokenEventCenter.removeListener(listener);
            SaManager.removeStpLogic(realm);
        }
    }

    /** \u9a8c\u8bc1<code>currentDao()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>chosen</code>\uff0c<code>chosen.inits</code> \u7b49\u4e8e <code>1</code>\uff0c<code>chosen.destroys</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void emptyDaoSlotIsRestoredWithoutInstallingAnImplicitDefault() {
        SaTokenDao original = currentDao();
        ProbeDao chosen = new ProbeDao();
        try {
            SaManager.setSaTokenDao(null);
            try (SaTokenRuntimeRegistration runtime = SaTokenRuntimeRegistration.builder().dao(chosen).build()) {
                runtime.start();
                runtime.start();
                assertSame(chosen, currentDao());
                assertEquals(1, chosen.inits);
            }
            assertNull(currentDao());
            assertEquals(1, chosen.destroys);
        } finally {
            SaManager.setSaTokenDao(original);
        }
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>currentDao()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>second</code>\uff0c<code>second.destroys</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void closingDependentOuterRegistrationBeforeInnerKeepsBothLive() {
        SaTokenDao original = currentDao();
        ProbeDao caller = new ProbeDao();
        ProbeDao first = new ProbeDao();
        ProbeDao second = new ProbeDao();
        SaTokenConfig callerConfig = SaManager.getConfig();
        SaTokenConfig outerConfig = new SaTokenConfig().setIsPrint(false);
        SaTokenConfig innerConfig = new SaTokenConfig().setIsPrint(false);
        SaTokenRuntimeRegistration outer = SaTokenRuntimeRegistration.builder().dao(first).nativeConfig(outerConfig).build();
        SaTokenRuntimeRegistration inner = SaTokenRuntimeRegistration.builder().dao(second).nativeConfig(innerConfig).build();
        try {
            SaManager.setSaTokenDao(caller);
            outer.start();
            inner.start();
            assertThrows(IllegalStateException.class, outer::close);
            assertSame(second, currentDao());
            inner.close();
            assertSame(first, currentDao());
            assertSame(outerConfig, first.configAtInit);
            outer.close();
            assertSame(caller, currentDao());
            assertSame(callerConfig, caller.configAtInit);
            assertEquals(2, caller.inits);
            assertEquals(1, caller.destroys);
            assertEquals(2, first.inits);
            assertEquals(2, first.destroys);
            assertEquals(1, second.inits);
            assertEquals(1, second.destroys);
        } finally {
            inner.close();
            outer.close();
            SaManager.setSaTokenDao(original);
        }
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>currentDao()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>shared</code>\uff0c<code>shared.destroys</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void sharingTheSameDaoKeepsItsOwnerLiveUntilAllBorrowersClose() {
        SaTokenDao original = currentDao();
        ProbeDao caller = new ProbeDao();
        ProbeDao shared = new ProbeDao();
        SaTokenRuntimeRegistration owner = SaTokenRuntimeRegistration.builder().dao(shared).build();
        SaTokenRuntimeRegistration first = SaTokenRuntimeRegistration.builder().dao(shared).build();
        SaTokenRuntimeRegistration second = SaTokenRuntimeRegistration.builder().dao(shared).build();
        try {
            SaManager.setSaTokenDao(caller);
            owner.start();
            first.start();
            second.start();
            assertThrows(IllegalStateException.class, owner::close);
            assertSame(shared, currentDao());
            assertEquals(1, shared.inits);
            assertEquals(0, shared.destroys);

            first.close();
            assertThrows(IllegalStateException.class, owner::close);
            assertSame(shared, currentDao());
            assertEquals(0, shared.destroys);
            second.close();
            owner.close();
            assertSame(caller, currentDao());
            assertEquals(1, shared.inits);
            assertEquals(1, shared.destroys);
        } finally {
            second.close();
            first.close();
            owner.close();
            SaManager.setSaTokenDao(original);
        }
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>SaManager.getSaJsonTemplate()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>shared</code>\uff0c<code>publications.get()</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void sharingTheSameJsonTemplateKeepsItsOwnerLiveWithoutRepublishing() {
        SaJsonTemplate previous = SaManager.getSaJsonTemplate();
        SaJsonTemplate shared = new SaJsonTemplateDefaultImpl();
        SaTokenRuntimeRegistration owner = SaTokenRuntimeRegistration.builder().jsonTemplate(shared).build();
        SaTokenRuntimeRegistration borrower = SaTokenRuntimeRegistration.builder().jsonTemplate(shared).build();
        AtomicInteger publications = new AtomicInteger();
        SaTokenListenerForSimple listener = new SaTokenListenerForSimple() {
            @Override
            public void doRegisterComponent(String name, Object component) {
                if (component == shared) {
                    publications.incrementAndGet();
                }
            }
        };
        SaTokenEventCenter.registerListener(listener);
        try {
            owner.start();
            borrower.start();
            assertThrows(IllegalStateException.class, owner::close);
            assertSame(shared, SaManager.getSaJsonTemplate());
            borrower.close();
            owner.close();
            assertSame(previous, SaManager.getSaJsonTemplate());
            assertEquals(1, publications.get());
        } finally {
            borrower.close();
            owner.close();
            SaTokenEventCenter.removeListener(listener);
            SaManager.setSaJsonTemplate(previous);
        }
    }

    /** \u9a8c\u8bc1<code>currentDao()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>external</code>\uff0c<code>shared.destroys</code> \u7b49\u4e8e <code>1</code>\uff0c<code>external.destroys</code> \u7b49\u4e8e <code>0</code>\u3002 */
    @Test
    void externalDaoReplacementLetsTheOldOwnerCloseBeforeItsBorrower() {
        SaTokenDao original = currentDao();
        ProbeDao shared = new ProbeDao();
        ProbeDao external = new ProbeDao();
        SaTokenRuntimeRegistration owner = SaTokenRuntimeRegistration.builder().dao(shared).build();
        SaTokenRuntimeRegistration borrower = SaTokenRuntimeRegistration.builder().dao(shared).build();
        try {
            owner.start();
            borrower.start();
            SaManager.setSaTokenDao(external);
            owner.close();
            borrower.close();
            assertSame(external, currentDao());
            assertEquals(1, shared.destroys);
            assertEquals(1, external.inits);
            assertEquals(0, external.destroys);
        } finally {
            borrower.close();
            owner.close();
            SaManager.setSaTokenDao(original);
        }
    }

    /** \u9a8c\u8bc1<code>SaManager.getSaTokenContext()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>originalContext</code>\uff0c<code>currentDao()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>choice</code>\uff0c<code>caller.destroys</code> \u7b49\u4e8e <code>0</code>\u3002 */
    @Test
    void disjointRegistrationAndExternallyReplacedDaoRemainUsable() {
        SaTokenDao original = currentDao();
        SaTokenContext originalContext = SaManager.getSaTokenContext();
        SaTokenContext outerContext = new SaTokenContextForThreadLocal();
        ProbeDao choice = new ProbeDao();
        ProbeDao caller = new ProbeDao();
        SaTokenRuntimeRegistration outer = SaTokenRuntimeRegistration.builder().context(outerContext).build();
        SaTokenRuntimeRegistration inner = SaTokenRuntimeRegistration.builder().dao(choice).build();
        try {
            SaManager.setSaTokenDao(null);
            outer.start();
            inner.start();
            outer.close();
            assertSame(originalContext, SaManager.getSaTokenContext());
            assertSame(choice, currentDao());

            SaManager.setSaTokenDao(caller);
            inner.close();
            assertSame(caller, currentDao());
            assertEquals(1, caller.inits);
            assertEquals(0, caller.destroys);
        } finally {
            inner.close();
            outer.close();
            SaManager.setSaTokenContext(originalContext);
            SaManager.setSaTokenDao(original);
        }
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>currentDao()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>caller</code>\uff0c<code>caller.inits</code> \u7b49\u4e8e <code>2</code>\u3002 */
    @Test
    void failedDaoInitRollsBackInstalledDaoAndOtherOwnedSlots() {
        SaTokenDao original = currentDao();
        SaTokenContext originalContext = SaManager.getSaTokenContext();
        ProbeDao caller = new ProbeDao();
        IllegalStateException originalFailure = new IllegalStateException("DAO 初始化失败");
        ProbeDao broken = new ProbeDao() {
            @Override
            public void init() {
                throw originalFailure;
            }
        };
        try {
            SaManager.setSaTokenDao(caller);
            try (SaTokenRuntimeRegistration runtime = SaTokenRuntimeRegistration.builder()
                    .context(new SaTokenContextForThreadLocal()).dao(broken).build()) {
                assertSame(originalFailure, assertThrows(IllegalStateException.class, runtime::start));
                assertSame(caller, currentDao());
                assertSame(originalContext, SaManager.getSaTokenContext());
                assertEquals(1, broken.destroys);
                assertEquals(2, caller.inits);
            }
        } finally {
            SaManager.setSaTokenContext(originalContext);
            SaManager.setSaTokenDao(original);
        }
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>SaManager.getSaJsonTemplate()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>originalJson</code>\uff0c<code>currentDao()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void failedDaoDestroyDoesNotSkipJsonRestoration() {
        SaTokenDao original = currentDao();
        SaJsonTemplate originalJson = SaManager.getSaJsonTemplate();
        SaJsonTemplate installedJson = new SaJsonTemplateDefaultImpl();
        IllegalStateException originalFailure = new IllegalStateException("DAO 销毁失败");
        ProbeDao broken = new ProbeDao() {
            @Override
            public void destroy() {
                super.destroy();
                if (destroys == 1) {
                    throw originalFailure;
                }
            }
        };
        try {
            SaManager.setSaTokenDao(null);
            SaTokenRuntimeRegistration runtime = SaTokenRuntimeRegistration.builder()
                    .dao(broken).jsonTemplate(installedJson).build();
            runtime.start();
            assertSame(originalFailure, assertThrows(IllegalStateException.class, runtime::close));
            assertSame(originalJson, SaManager.getSaJsonTemplate());
            assertSame(broken, currentDao());
            runtime.close();
            assertNull(currentDao());
        } finally {
            SaManager.setSaJsonTemplate(originalJson);
            SaManager.setSaTokenDao(original);
        }
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>currentDao()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>caller</code>\uff0c<code>installed.destroys</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void closeRetriesInitializationOfTheAlreadyPublishedPreviousDao() {
        SaTokenDao original = currentDao();
        IllegalStateException failure = new IllegalStateException("原 DAO 重新初始化失败");
        ProbeDao caller = failsFirstRestoration(failure);
        ProbeDao installed = new ProbeDao();
        try {
            SaManager.setSaTokenDao(caller);
            try (SaTokenRuntimeRegistration runtime = SaTokenRuntimeRegistration.builder().dao(installed).build()) {
                runtime.start();
                assertSame(failure, assertThrows(IllegalStateException.class, runtime::close));
                assertSame(caller, currentDao());
                assertEquals(2, caller.inits);
                runtime.close();
                assertSame(caller, currentDao());
                assertEquals(3, caller.inits);
                assertEquals(2, caller.destroys);
                assertEquals(1, installed.destroys);
            }
        } finally {
            SaManager.setSaTokenDao(original);
        }
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>currentDao()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>caller</code>\uff0c<code>external.destroys</code> \u7b49\u4e8e <code>0</code>\u3002 */
    @Test
    void closeLeavesTheExternalReplacementAfterPreviousDaoInitializationFails() {
        SaTokenDao original = currentDao();
        IllegalStateException failure = new IllegalStateException("原 DAO 重新初始化失败");
        ProbeDao caller = failsFirstRestoration(failure);
        ProbeDao external = new ProbeDao();
        try {
            SaManager.setSaTokenDao(caller);
            try (SaTokenRuntimeRegistration runtime = SaTokenRuntimeRegistration.builder().dao(new ProbeDao()).build()) {
                runtime.start();
                assertSame(failure, assertThrows(IllegalStateException.class, runtime::close));
                assertSame(caller, currentDao());
                SaManager.setSaTokenDao(external);
                runtime.close();
                assertSame(external, currentDao());
                assertEquals(2, caller.inits);
                assertEquals(1, external.inits);
                assertEquals(0, external.destroys);
            }
        } finally {
            SaManager.setSaTokenDao(original);
        }
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>initFailure.getSuppressed().length</code> \u7b49\u4e8e <code>1</code>\uff0c<code>caller.inits</code> \u7b49\u4e8e <code>2</code>\u3002 */
    @Test
    void failedStartKeepsPrimaryFailureAndAllowsCleanupRetry() {
        SaTokenDao original = currentDao();
        SaTokenContext originalContext = SaManager.getSaTokenContext();
        ProbeDao caller = new ProbeDao();
        IllegalStateException initFailure = new IllegalStateException("DAO 初始化失败");
        IllegalStateException destroyFailure = new IllegalStateException("DAO 销毁失败");
        ProbeDao broken = new ProbeDao() {
            @Override
            public void init() {
                throw initFailure;
            }

            @Override
            public void destroy() {
                super.destroy();
                if (destroys == 1) {
                    throw destroyFailure;
                }
            }
        };
        try {
            SaManager.setSaTokenDao(caller);
            try (SaTokenRuntimeRegistration runtime = SaTokenRuntimeRegistration.builder()
                    .context(new SaTokenContextForThreadLocal()).dao(broken).build()) {
                assertSame(initFailure, assertThrows(IllegalStateException.class, runtime::start));
                assertEquals(1, initFailure.getSuppressed().length);
                assertSame(destroyFailure, initFailure.getSuppressed()[0]);
                assertSame(originalContext, SaManager.getSaTokenContext());
                assertSame(broken, currentDao());
                assertThrows(IllegalStateException.class, runtime::start);
                runtime.close();
                assertSame(caller, currentDao());
                assertEquals(2, caller.inits);
            }
        } finally {
            SaManager.setSaTokenContext(originalContext);
            SaManager.setSaTokenDao(original);
        }
    }

    /** \u66ff\u6362 Sa-Token \u4f1a\u8bdd\u5b58\u50a8\u5e76\u66b4\u9732\u53ef\u68c0\u67e5\u7684\u521d\u59cb\u5316\u6216\u6e05\u7406\u884c\u4e3a\u3002 */
    private static class ProbeDao extends SaTokenDaoDefaultImpl {

        /** \u8ddf\u8e2a\u5e95\u5c42\u7ec4\u4ef6\u521d\u59cb\u5316\u6b21\u6570\u7684\u8ba1\u6570\u5668\u3002 */
        int inits;
        /** \u8ddf\u8e2a\u5e95\u5c42\u7ec4\u4ef6\u9500\u6bc1\u6b21\u6570\u7684\u8ba1\u6570\u5668\u3002 */
        int destroys;
        /** \u5e95\u5c42\u7ec4\u4ef6\u521d\u59cb\u5316\u65f6\u89c2\u5bdf\u5230\u7684\u539f\u751f\u914d\u7f6e\u3002 */
        SaTokenConfig configAtInit;

        /** \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>configAtInit = SaManager.config</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002 */
        @Override
        public void init() {
            inits++;
            configAtInit = SaManager.config;
        }

        /** \u589e\u52a0\u9500\u6bc1\u6b21\u6570\u4ee5\u4f9b\u6d4b\u8bd5\u68c0\u67e5\u539f\u751f\u4f1a\u8bdd\u5b58\u50a8\u7684\u6e05\u7406\u3002 */
        @Override
        public void destroy() {
            destroys++;
        }
    }
}
