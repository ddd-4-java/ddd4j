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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SaTokenRuntimeRegistrationTest {

    @Test
    void sessionConfigOverridesDefaultsAndRestoresOwnedComponents() {
        SaTokenConfig previousConfig = SaManager.getConfig();
        StpLogic previousLogic = StpUtil.stpLogic;
        SaTokenContext previousContext = SaManager.getSaTokenContext();
        StpInterface previousPermissions = SaManager.getStpInterface();
        SubjectDataProvider previousData = SubjectKit.dataProvider;
        SaTokenConfig config = new SaTokenConfig().setTokenName("native").setTokenPrefix("Bearer").setIsPrint(false);
        SaTokenContext context = new SaTokenContextForThreadLocal();
        SubjectDataProvider provider = new SubjectDataProvider() { };
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

    private static SaTokenDao currentDao() {
        return (SaTokenDao) ReflectUtil.getStaticFieldValue(ReflectUtil.getField(SaManager.class, "saTokenDao"));
    }

    private static class ProbeDao extends SaTokenDaoDefaultImpl {

        int inits;
        int destroys;
        SaTokenConfig configAtInit;

        @Override
        public void init() {
            inits++;
            configAtInit = SaManager.config;
        }

        @Override
        public void destroy() {
            destroys++;
        }
    }
}
