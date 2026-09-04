package io.ddd4j.auth.satoken.subject;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import io.ddd4j.auth.satoken.config.Ddd4jStpLogicJwtForSimple;
import io.ddd4j.auth.satoken.config.SaTokenAuthenticationMode;
import io.ddd4j.auth.satoken.config.SaTokenSecurityConfigurer;
import io.ddd4j.auth.satoken.config.SaTokenSecurityProperties;
import io.ddd4j.core.auth.AuthLogoutMode;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.auth.session.AuthCookieConfig;
import io.ddd4j.core.auth.session.AuthSessionConfig;
import io.ddd4j.core.exception.AccountDisabledException;
import io.ddd4j.core.exception.NotLoggedInException;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.util.SubjectKit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SaTokenSubject} 全生命周期契约测试（纯 Java，零 Spring）。
 */
class SaTokenSubjectLifecycleTest {

    private SaTokenConfig originalConfig;
    private SaTokenDao originalDao;
    private StpLogic originalLogic;
    private SubjectDataProvider originalDataProvider;

    @BeforeEach
    void setUp() {
        originalConfig = SaManager.getConfig();
        originalDao = SaManager.getSaTokenDao();
        originalLogic = StpUtil.stpLogic;
        originalDataProvider = SubjectKit.dataProvider;
        SaManager.setConfig(new SaTokenConfig()
                .setJwtSecretKey("ddd4j-satoken-lifecycle-test-secret")
                .setIsPrint(false)
                .setIsLog(false));
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        cn.dev33.satoken.context.mock.SaTokenContextMockUtil.setMockContext();
    }

    @AfterEach
    void tearDown() {
        cn.dev33.satoken.context.mock.SaTokenContextMockUtil.clearContext();
        StpUtil.setStpLogic(originalLogic);
        SaManager.setSaTokenDao(originalDao);
        SaManager.setConfig(originalConfig);
        SubjectKit.setDataProvider(originalDataProvider);
    }

    private SaTokenSubject subject() {
        return new SaTokenSubject();
    }

    private AuthPrincipal principal(String loginId) {
        return new AuthPrincipal().setLoginId(loginId).setUserId("user-" + loginId)
                .setOrgId("org-1").setRoleId("role-1");
    }

    private String login(String loginId) {
        return subject().login(AuthRequest.of(loginId).setPrincipal(principal(loginId)));
    }

    @Test
    void loginBuildsFullSessionFromConfig() {
        AuthSessionConfig sessionConfig = new AuthSessionConfig();
        sessionConfig.setTimeout(600);
        sessionConfig.setDeviceType("PC");
        sessionConfig.setActiveTimeout(300L);
        sessionConfig.setConcurrent(true);
        sessionConfig.setShare(true);
        sessionConfig.setMaxLoginCount(3);
        sessionConfig.setOverflowLogoutMode(AuthLogoutMode.KICKOUT);
        sessionConfig.setWriteTokenToHeader(false);
        sessionConfig.setCreateTokenSessionNow(true);
        sessionConfig.setPresetToken("preset-token-1");
        AuthCookieConfig cookie = new AuthCookieConfig();
        cookie.setDomain("example.com");
        cookie.setPath("/api");
        cookie.setSecure(true);
        cookie.setHttpOnly(false);
        cookie.setSameSite("Strict");
        sessionConfig.setCookie(cookie);
        AuthRequest request = AuthRequest.of(1001L).setPrincipal(principal("1001"))
                .setSessionConfig(sessionConfig).extra("channel", "web");

        String token = subject().login(request);

        assertNotNull(token);
        assertTrue(StpUtil.isLogin(1001L));
        assertEquals("user-1001", subject().getPrincipalByLoginId(1001L).getUserId());
    }

    @Test
    void loginWithoutSessionConfigUsesDefaults() {
        String token = subject().login(new AuthRequest("user-2"));

        assertNotNull(token);
        assertTrue(StpUtil.isLogin("user-2"));
    }

    @Test
    void loginMapsDisabledAccountToAccountDisabledException() {
        SaTokenSubject saSubject = new SaTokenSubject() {
            @Override
            protected StpLogic stpLogic(String realm) {
                return new StpLogic(StpUtil.TYPE) {
                    @Override
                    public void login(Object id, SaLoginParameter param) {
                        throw new cn.dev33.satoken.exception.DisableServiceException(
                                "login", "banned", "login", 0, 0, 3600L);
                    }
                };
            }
        };

        assertThrows(AccountDisabledException.class, () -> saSubject.login(AuthRequest.of("banned")));
    }

    @Test
    void loginMapsNotLoginFailureToNotLoggedInException() {
        SaTokenSubject saSubject = new SaTokenSubject() {
            @Override
            protected StpLogic stpLogic(String realm) {
                return new StpLogic(StpUtil.TYPE) {
                    @Override
                    public void login(Object id, SaLoginParameter param) {
                        throw new NotLoginException(NotLoginException.INVALID_TOKEN,
                                "account invalid", "login");
                    }
                };
            }
        };

        assertThrows(NotLoggedInException.class, () -> saSubject.login(AuthRequest.of("ghost")));
    }

    @Test
    void loginMapsGenericFailureToNotLoggedInException() {
        SaTokenSubject saSubject = new SaTokenSubject() {
            @Override
            protected StpLogic stpLogic(String realm) {
                return new StpLogic(StpUtil.TYPE) {
                    @Override
                    public void login(Object id, SaLoginParameter param) {
                        throw new IllegalStateException("broker unavailable");
                    }
                };
            }
        };

        assertThrows(NotLoggedInException.class, () -> saSubject.login(AuthRequest.of("ghost")));
    }

    @Test
    void getPrincipalResolvesActiveSession() {
        login("user-3");

        AuthPrincipal principal = subject().getPrincipal();

        assertEquals("user-user-3", principal.getUserId());
    }

    @Test
    void getPrincipalReturnsNullWhenNotLoggedIn() {
        assertNull(subject().getPrincipal());
    }

    @Test
    void getPrincipalByLoginIdReturnsNullForUnknownAccount() {
        assertNull(subject().getPrincipalByLoginId("nobody"));
    }

    @Test
    void getPrincipalByTokenDelegatesToVerify() {
        String token = login("user-4");

        AuthPrincipal principal = subject().getPrincipalByToken(token);

        assertEquals("user-user-4", principal.getUserId());
    }

    @Test
    void logoutTerminatesCurrentSession() {
        login("user-5");
        assertTrue(StpUtil.isLogin());

        subject().logout();

        assertFalse(StpUtil.isLogin());
    }

    @Test
    void logoutAndKickoutByLoginId() {
        login("user-6");
        assertTrue(StpUtil.isLogin("user-6"));

        subject().logout("user-6");
        assertFalse(StpUtil.isLogin("user-6"));

        login("user-7");
        subject().kickout("user-7");
        assertFalse(StpUtil.isLogin("user-7"));
    }

    @Test
    void refreshRenewsActiveSessionTimeout() {
        String token = login("user-8");
        SaManager.getSaTokenDao().updateTimeout(StpUtil.stpLogic.splicingKeyTokenValue(token), 30);
        assertEquals(30, SaManager.getSaTokenDao().getTimeout(StpUtil.stpLogic.splicingKeyTokenValue(token)));

        String refreshed = subject().refresh();

        assertEquals(token, refreshed);
        assertTrue(SaManager.getSaTokenDao().getTimeout(
                StpUtil.stpLogic.splicingKeyTokenValue(token)) >= 60);
    }

    @Test
    void verifyRejectsBlankToken() {
        assertNull(subject().verify(""));
        assertNull(subject().verify(null));
    }

    @Test
    void permissionMatrixReflectsDataProvider() {
        login("user-9");
        AtomicReference<List<String>> permissions =
                new AtomicReference<>(Arrays.asList("order:read", "user:write"));
        SubjectKit.setDataProvider(new SubjectDataProvider() {
            @Override
            public List<String> getPermissionList(AuthPrincipal ignored) {
                return permissions.get();
            }
        });
        SaTokenSubject saSubject = subject();

        assertTrue(saSubject.isPermitted("order:read"));
        assertFalse(saSubject.isPermitted("order:delete"));
        assertTrue(saSubject.isPermitted("user-9", "order:read"));
        assertTrue(saSubject.isPermittedAny("order:delete", "user:write"));
        assertFalse(saSubject.isPermittedAny("order:delete"));
        assertTrue(saSubject.isPermittedAll("order:read", "user:write"));
        assertFalse(saSubject.isPermittedAll("order:read", "order:delete"));
        assertArrayEquals(new boolean[]{true, false},
                saSubject.isPermitted(new String[]{"order:read", "order:delete"}));
        assertArrayEquals(new boolean[]{true, false},
                saSubject.isPermitted("user-9", new String[]{"order:read", "order:delete"}));
        assertTrue(saSubject.isPermittedAny("user-9", new String[]{"order:delete", "user:write"}));
        assertTrue(saSubject.isPermittedAll("user-9", new String[]{"order:read", "user:write"}));
    }

    @Test
    void permissionChecksReturnDefaultsWhenNotLoggedInOrEmpty() {
        SaTokenSubject saSubject = subject();

        assertFalse(saSubject.isPermitted("anything"));
        assertFalse(saSubject.isPermitted("nobody", "anything"));
        assertEquals(0, saSubject.isPermitted().length);
        assertEquals(0, saSubject.isPermitted(new String[]{"user-9"}).length);
        assertFalse(saSubject.isPermittedAny());
        assertFalse(saSubject.isPermittedAll());
        assertFalse(saSubject.isPermittedAny("user-9"));
        assertFalse(saSubject.isPermittedAll("user-9"));
    }

    @Test
    void roleMatrixReflectsDataProvider() {
        login("user-10");
        AtomicReference<List<String>> roles = new AtomicReference<>(Arrays.asList("admin", "editor"));
        SubjectKit.setDataProvider(new SubjectDataProvider() {
            @Override
            public List<String> getRoleList(AuthPrincipal ignored) {
                return roles.get();
            }
        });
        SaTokenSubject saSubject = subject();

        assertTrue(saSubject.hasRole("admin"));
        assertFalse(saSubject.hasRole("viewer"));
        assertTrue(saSubject.hasRole("user-10", "admin"));
        assertTrue(saSubject.hasAnyRole("viewer", "editor"));
        assertFalse(saSubject.hasAnyRole("viewer"));
        assertTrue(saSubject.hasAllRole("admin", "editor"));
        assertFalse(saSubject.hasAllRole("admin", "viewer"));
        assertArrayEquals(new boolean[]{true, false},
                saSubject.hasRoles("admin", "viewer"));
        assertArrayEquals(new boolean[]{true, false},
                saSubject.hasRoles("user-10", new String[]{"admin", "viewer"}));
        assertTrue(saSubject.hasAnyRole("user-10", new String[]{"viewer", "editor"}));
        assertTrue(saSubject.hasAllRole("user-10", new String[]{"admin", "editor"}));
    }

    @Test
    void roleChecksReturnDefaultsWhenNotLoggedInOrEmpty() {
        SaTokenSubject saSubject = subject();

        assertFalse(saSubject.hasRole("admin"));
        assertFalse(saSubject.hasRole("nobody", "admin"));
        assertEquals(0, saSubject.hasRoles().length);
        assertEquals(0, saSubject.hasRoles(new String[]{"user-10"}).length);
        assertFalse(saSubject.hasAnyRole());
        assertFalse(saSubject.hasAllRole());
        assertFalse(saSubject.hasAnyRole("user-10"));
        assertFalse(saSubject.hasAllRole("user-10"));
    }

    @Test
    void stateChecksReflectSession() {
        login("user-11");

        SaTokenSubject saSubject = subject();
        assertTrue(saSubject.isAuthenticated());
        assertTrue(saSubject.isAuthenticated("user-11"));
        assertFalse(saSubject.isAuthenticated("nobody"));
        assertFalse(saSubject.isRemembered());
        assertEquals("user-11", saSubject.getLoginId());
        assertEquals("user-user-11", saSubject.getUserId());
        assertEquals("org-1", saSubject.getOrgId());
        assertEquals("role-1", saSubject.getRoleId());
    }

    @Test
    void stateChecksReturnNullWhenNotLoggedIn() {
        SaTokenSubject saSubject = subject();

        assertFalse(saSubject.isAuthenticated());
        assertFalse(saSubject.isAuthenticated("nobody"));
        assertThrows(cn.dev33.satoken.exception.NotLoginException.class, saSubject::getLoginId);
        assertNull(saSubject.getUserId());
        assertNull(saSubject.getOrgId());
        assertNull(saSubject.getRoleId());
    }

    @Test
    void getExtraReadsTokenExtraData() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        properties.setJwtSecretKey("ddd4j-satoken-extra-test-secret-32bytes");
        properties.setJwtIssuer("ddd4j-test");
        properties.setJwtAudience("ddd4j-tests");
        SaTokenSecurityConfigurer.configure(properties);

        AuthRequest request = AuthRequest.of("user-12").setPrincipal(principal("user-12"))
                .extra("channel", "mobile");
        String token = subject().login(request);

        assertEquals("mobile", subject().getExtra(token, "channel"));
    }

    @Test
    void disableLifecycleDelegatesToSaToken() {
        SaTokenSubject saSubject = subject();

        assertFalse(saSubject.isDisabled("user-13"));
        saSubject.disable("user-13", 3600);
        assertTrue(saSubject.isDisabled("user-13"));
        saSubject.untieDisable("user-13");
        assertFalse(saSubject.isDisabled("user-13"));
    }

    @Test
    void loginToCustomRealmResolvesStpLogic() {
        SaTokenSubject saSubject = subject();
        AuthRequest request = AuthRequest.of("admin-1").setRealm("admin")
                .setPrincipal(principal("admin-1"));

        String token = saSubject.login(request);

        assertNotNull(token);
        assertTrue(SaManager.getStpLogic("admin").isLogin("admin-1"));
    }

    @Test
    void toSaLogoutModeMapsNullToLogout() {
        // 通过 overflowLogoutMode=null 触发 toSaLogoutMode(null) 分支
        AuthSessionConfig sessionConfig = new AuthSessionConfig();
        sessionConfig.setOverflowLogoutMode(null);
        AuthRequest request = AuthRequest.of("user-14").setPrincipal(principal("user-14"))
                .setSessionConfig(sessionConfig);

        String token = subject().login(request);

        assertNotNull(token);
    }

    @Test
    void verifyHandlesSessionAndJwtMismatchSafely() {
        login("user-15");
        Map<String, Object> extras = new HashMap<>();
        extras.put("channel", "web");

        // 非 JWT 模式下传入任意 token 应被拒
        assertNull(subject().verify("no-such-token"));
    }
    @Test
    void loginMapsReplacedOverflowMode() {
        AuthSessionConfig sessionConfig = new AuthSessionConfig();
        sessionConfig.setOverflowLogoutMode(AuthLogoutMode.REPLACED);
        AuthRequest request = AuthRequest.of("user-16").setPrincipal(principal("user-16"))
                .setSessionConfig(sessionConfig);

        String token = subject().login(request);

        assertNotNull(token);
    }

    @Test
    void loginAcceptsNullSessionConfig() {
        AuthRequest request = AuthRequest.of("user-17").setPrincipal(principal("user-17"));
        request.setSessionConfig(null);

        String token = subject().login(request);

        assertNotNull(token);
    }

    @Test
    void getPrincipalReturnsNullWhenSessionVanished() {
        String loginId = "user-18";
        login(loginId);
        SaManager.getSaTokenDao().delete(StpUtil.stpLogic.splicingKeySession(loginId));

        assertNull(subject().getPrincipal());
    }

    @Test
    void loginIdVariantChecksReturnDefaultsForUnknownAccount() {
        SaTokenSubject saSubject = subject();

        assertEquals(0, saSubject.isPermitted("nobody", new String[]{"order:read"}).length);
        assertEquals(0, saSubject.isPermitted("nobody", (String[]) null).length);
        assertFalse(saSubject.isPermittedAny("nobody", new String[]{"order:read"}));
        assertFalse(saSubject.isPermittedAny("nobody", (String[]) null));
        assertFalse(saSubject.isPermittedAll("nobody", new String[]{"order:read"}));
        assertFalse(saSubject.isPermittedAll("nobody", (String[]) null));
        assertEquals(0, saSubject.hasRoles("nobody", new String[]{"admin"}).length);
        assertEquals(0, saSubject.hasRoles("nobody", (String[]) null).length);
        assertFalse(saSubject.hasAnyRole("nobody", new String[]{"admin"}));
        assertFalse(saSubject.hasAnyRole("nobody", (String[]) null));
        assertFalse(saSubject.hasAllRole("nobody", new String[]{"admin"}));
        assertFalse(saSubject.hasAllRole("nobody", (String[]) null));
    }

    @Test
    void isTrustDeviceIdDelegatesWithJwtContext() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        properties.setJwtSecretKey("ddd4j-satoken-trust-test-secret-32bytes");
        properties.setJwtIssuer("ddd4j-test");
        properties.setJwtAudience("ddd4j-tests");
        SaTokenSecurityConfigurer.configure(properties);

        SaTokenSubject saSubject = subject();
        saSubject.login(AuthRequest.of("user-19").setPrincipal(principal("user-19"))
                .extra(io.ddd4j.core.constant.AuthConstants.FIELD_USER_ID, 1001L));

        assertFalse(saSubject.isTrustDeviceId("device-9"));
        assertFalse(saSubject.isTrustDeviceId(1001L, "device-9"));
    }
}
