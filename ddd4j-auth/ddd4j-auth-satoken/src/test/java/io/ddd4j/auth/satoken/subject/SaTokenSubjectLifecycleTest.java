/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.auth.satoken.subject;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import io.ddd4j.auth.satoken.config.SaTokenAuthenticationMode;
import io.ddd4j.auth.satoken.config.SaTokenSecurityConfigurer;
import io.ddd4j.auth.satoken.config.SaTokenSecurityProperties;
import io.ddd4j.core.auth.*;
import io.ddd4j.core.auth.session.AuthCookieConfig;
import io.ddd4j.core.auth.session.AuthSessionConfig;
import io.ddd4j.core.exception.AccountDisabledException;
import io.ddd4j.core.exception.NotLoggedInException;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SaTokenSubject} 全生命周期契约测试（纯 Java，零 Spring）。
 */
class SaTokenSubjectLifecycleTest {

    /**
     * 进入测试场景前注册的配置。
     */
    private SaTokenConfig originalConfig;
    /**
     * 进入测试场景前注册的会话存储。
     */
    private SaTokenDao originalDao;
    /**
     * 进入测试场景前注册的认证逻辑。
     */
    private StpLogic originalLogic;
    /**
     * 进入当前执行范围前保存的组件，退出时用于恢复 <code>SubjectDataProvider</code>。
     */
    private SubjectDataProvider originalDataProvider;
    /**
     * 用例修改前注册的服务提供器，供用例结束时恢复。
     */
    private SubjectProvider originalProvider;

    /**
     * 测试前依次恢复或安装配置<code>SaManager</code>、恢复或安装原生存储<code>SaManager</code>。
     */
    @BeforeEach
    void setUp() {
        originalConfig = SaManager.getConfig();
        originalDao = SaManager.getSaTokenDao();
        originalLogic = StpUtil.stpLogic;
        originalDataProvider = SubjectKit.dataProvider;
        originalProvider = SubjectKit.subjectProvider;
        SaManager.setConfig(new SaTokenConfig()
                .setJwtSecretKey("ddd4j-satoken-lifecycle-test-secret")
                .setIsPrint(false)
                .setIsLog(false));
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        cn.dev33.satoken.context.mock.SaTokenContextMockUtil.setMockContext();
    }

    /**
     * 测试后依次清除上下文<code>cn.dev33.satoken.context.mock.SaTokenContextMockUtil</code>、恢复或安装登录逻辑<code>StpUtil</code>、恢复或安装原生存储<code>SaManager</code>、恢复或安装配置<code>SaManager</code>、注册<code>SubjectKit</code>。
     */
    @AfterEach
    void tearDown() {
        cn.dev33.satoken.context.mock.SaTokenContextMockUtil.clearContext();
        StpUtil.setStpLogic(originalLogic);
        SaManager.setSaTokenDao(originalDao);
        SaManager.setConfig(originalConfig);
        SubjectKit.setDataProvider(originalDataProvider);
        SubjectKit.register(originalProvider);
    }

    /**
     * 创建 <code>new SaTokenSubject</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new SaTokenSubject()</code>
     */
    private SaTokenSubject subject() {
        return new SaTokenSubject();
    }

    /**
     * 创建 <code>new AuthPrincipal</code> 供当前测试路径调用。
     *
     * @param loginId 测试会话使用的账号标识
     * @return 当前测试使用的结果，取自 <code>new AuthPrincipal().setLoginId(loginId).setUserId("user-" + loginId).setOrgId("org-1").setRoleId("role-1")</code>
     */
    private AuthPrincipal principal(String loginId) {
        return new AuthPrincipal().setLoginId(loginId).setUserId("user-" + loginId)
                .setOrgId("org-1").setRoleId("role-1");
    }

    /**
     * 通过 <code>subject()</code> 取得当前测试需要的结果。
     *
     * @param loginId 测试会话使用的账号标识
     * @return 当前测试使用的结果，取自 <code>subject().login(AuthRequest.of(loginId).setPrincipal(principal(loginId)))</code>
     */
    private String login(String loginId) {
        return subject().login(AuthRequest.of(loginId).setPrincipal(principal(loginId)));
    }

    /**
     * 验证<code>token</code> 等于 <code>"preset-token-1"</code>，<code>SaManager.getSaTokenDao().getSession(StpUtil.stpLogic.splicingKeyTokenSession(token))</code>非空，<code>subject().getPrincipalByLoginId(1001L).getUserId()</code> 等于 <code>"user-1001"</code>。
     */
    @Test
    void loginBuildsFullSessionFromConfig() {
        AuthSessionConfig sessionConfig = new AuthSessionConfig();
        sessionConfig.setTimeout(600);
        sessionConfig.setDeviceType("PC");
        sessionConfig.setDeviceId("managed-device");
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

        assertEquals("preset-token-1", token);
        assertNotNull(SaManager.getSaTokenDao().getSession(StpUtil.stpLogic.splicingKeyTokenSession(token)));
        assertEquals("managed-device", StpUtil.stpLogic.getTerminalInfoByToken(token).getDeviceId());
        assertTrue(StpUtil.isLogin(1001L));
        assertEquals("user-1001", subject().getPrincipalByLoginId(1001L).getUserId());
    }

    /**
     * 验证<code>StpUtil.stpLogic.getTerminalInfoByToken(token).getDeviceId()</code> 等于 <code>deviceId</code>，<code>subject().isTrustDeviceId(loginId, deviceId)</code>为真，<code>subject().isTrustDeviceId(loginId, "different-installation")</code>为假。
     */
    @Test
    void loginRegistersConfiguredDeviceId() {
        String loginId = "registered-device-account";
        String deviceId = "browser-installation-1";
        AuthSessionConfig config = new AuthSessionConfig().setDeviceType("desktop").setDeviceId(deviceId);

        String token = subject().login(AuthRequest.of(loginId).setSessionConfig(config));

        assertEquals(deviceId, StpUtil.stpLogic.getTerminalInfoByToken(token).getDeviceId());
        assertTrue(subject().isTrustDeviceId(loginId, deviceId));
        assertFalse(subject().isTrustDeviceId(loginId, "different-installation"));
    }

    /**
     * 验证<code>dao.getSession(logic.splicingKeySession("lazy-token-session"))</code>非空，<code>dao.getSession(logic.splicingKeySession("eager-token-session"))</code>非空，<code>dao.getSession(logic.splicingKeyTokenSession(eagerToken))</code>非空。
     */
    @Test
    void loginCreatesTokenSessionOnlyForEagerLogin() {
        StpLogic logic = StpUtil.stpLogic;
        SaTokenDao dao = SaManager.getSaTokenDao();
        String lazyToken = subject().login(AuthRequest.of("lazy-token-session"));
        AuthSessionConfig eager = new AuthSessionConfig().setCreateTokenSessionNow(true);
        String eagerToken = subject().login(AuthRequest.of("eager-token-session").setSessionConfig(eager));

        assertNotNull(dao.getSession(logic.splicingKeySession("lazy-token-session")));
        assertNotNull(dao.getSession(logic.splicingKeySession("eager-token-session")));
        assertNull(dao.getSession(logic.splicingKeyTokenSession(lazyToken)));
        assertNotNull(dao.getSession(logic.splicingKeyTokenSession(eagerToken)));
    }

    /**
     * 验证<code>subject().verify(desktopToken)</code>非空，<code>subject().verify(oldMobileToken)</code>为空，<code>subject().verify(newMobileToken)</code>非空。
     */
    @Test
    void loginReplacesOnlyCurrentDeviceTypeByDefault() {
        String loginId = "replace-current-device-type";
        AuthSessionConfig desktop = new AuthSessionConfig().setDeviceType("desktop");
        AuthSessionConfig mobile = new AuthSessionConfig().setDeviceType("mobile");
        String desktopToken = subject().login(AuthRequest.of(loginId)
                .setSessionConfig(desktop).setPrincipal(principal(loginId)));
        String oldMobileToken = subject().login(AuthRequest.of(loginId)
                .setSessionConfig(mobile).setPrincipal(principal(loginId)));

        String newMobileToken = subject().login(AuthRequest.of(loginId)
                .setSessionConfig(mobile.setConcurrent(false)).setPrincipal(principal(loginId)));

        assertNotNull(subject().verify(desktopToken));
        assertNull(subject().verify(oldMobileToken));
        assertNotNull(subject().verify(newMobileToken));
    }

    /**
     * 验证<code>token</code>非空，<code>StpUtil.isLogin("user-2")</code>为真。
     */
    @Test
    void loginWithoutSessionConfigUsesDefaults() {
        String token = subject().login(new AuthRequest("user-2"));

        assertNotNull(token);
        assertTrue(StpUtil.isLogin("user-2"));
    }

    /**
     * 验证<code>saSubject.login(AuthRequest.of("banned"))</code> 抛出 <code>AccountDisabledException.class</code>。
     */
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

    /**
     * 验证<code>saSubject.login(AuthRequest.of("ghost"))</code> 抛出 <code>NotLoggedInException.class</code>。
     */
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

    /**
     * 验证<code>saSubject.login(AuthRequest.of("ghost"))</code> 抛出 <code>NotLoggedInException.class</code>。
     */
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

    /**
     * 验证<code>principal.getUserId()</code> 等于 <code>"user-user-3"</code>。
     */
    @Test
    void getPrincipalResolvesActiveSession() {
        login("user-3");

        AuthPrincipal principal = subject().getPrincipal();

        assertEquals("user-user-3", principal.getUserId());
    }

    /**
     * 验证<code>subject().getPrincipal()</code>为空。
     */
    @Test
    void getPrincipalReturnsNullWhenNotLoggedIn() {
        assertNull(subject().getPrincipal());
    }

    /**
     * 验证<code>subject().getPrincipalByLoginId("nobody")</code>为空。
     */
    @Test
    void getPrincipalByLoginIdReturnsNullForUnknownAccount() {
        assertNull(subject().getPrincipalByLoginId("nobody"));
    }

    /**
     * 验证<code>principal.getUserId()</code> 等于 <code>"user-user-4"</code>。
     */
    @Test
    void getPrincipalByTokenDelegatesToVerify() {
        String token = login("user-4");

        AuthPrincipal principal = subject().getPrincipalByToken(token);

        assertEquals("user-user-4", principal.getUserId());
    }

    /**
     * 验证<code>StpUtil.isLogin()</code>为真，<code>StpUtil.isLogin()</code>为假。
     */
    @Test
    void logoutTerminatesCurrentSession() {
        login("user-5");
        assertTrue(StpUtil.isLogin());

        subject().logout();

        assertFalse(StpUtil.isLogin());
    }

    /**
     * 验证<code>StpUtil.isLogin("user-6")</code>为真，<code>StpUtil.isLogin("user-6")</code>为假，<code>StpUtil.isLogin("user-7")</code>为假。
     */
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

    /**
     * 验证<code>initialTimeout &gt; 0 &amp;&amp; initialTimeout &lt;= 30</code>为真，<code>refreshed</code> 等于 <code>token</code>，<code>SaManager.getSaTokenDao().getTimeout( StpUtil.stpLogic.splicingKeyTokenValue(token)) &gt;= 60</code>为真。
     */
    @Test
    void refreshRenewsActiveSessionTimeout() {
        String token = login("user-8");
        SaManager.getSaTokenDao().updateTimeout(StpUtil.stpLogic.splicingKeyTokenValue(token), 30);
        long initialTimeout = SaManager.getSaTokenDao().getTimeout(StpUtil.stpLogic.splicingKeyTokenValue(token));
        assertTrue(initialTimeout > 0 && initialTimeout <= 30);

        String refreshed = subject().refresh();

        assertEquals(token, refreshed);
        assertTrue(SaManager.getSaTokenDao().getTimeout(
                StpUtil.stpLogic.splicingKeyTokenValue(token)) >= 60);
    }

    /**
     * 验证<code>ttl &gt; 0 &amp;&amp; ttl &lt;= 300</code>为真。
     */
    @Test
    void defaultSessionInheritsConfiguredTokenTtl() {
        SaManager.getConfig().setTimeout(300);
        String token = login("ttl-default");

        long ttl = StpUtil.stpLogic.getTokenTimeout(token);
        assertTrue(ttl > 0 && ttl <= 300, "Expected configured finite TTL, got " + ttl);
    }

    /**
     * 验证<code>StpUtil.stpLogic.getTokenTimeout(token)</code> 等于 <code>SaTokenDao.NEVER_EXPIRE</code>。
     */
    @Test
    void explicitPermanentSessionRetainsItsMeaning() {
        SaManager.getConfig().setTimeout(300);
        String token = subject().login(AuthRequest.of("ttl-permanent")
                .setSessionConfig(new AuthSessionConfig().setTimeout(-1)));

        assertEquals(SaTokenDao.NEVER_EXPIRE, StpUtil.stpLogic.getTokenTimeout(token));
    }

    /**
     * 验证<code>subject().refresh()</code> 抛出 <code>NotLoggedInException.class</code>。
     */
    @Test
    void refreshRejectsRevokedToken() {
        String token = login("refresh-revoked");
        StpUtil.stpLogic.logoutByTokenValue(token);

        assertThrows(NotLoggedInException.class, () -> subject().refresh());
    }

    /**
     * 验证<code>admin.verify(adminToken).getUserId()</code> 等于 <code>"admin"</code>，<code>user.verify(userToken).getUserId()</code> 等于 <code>"user"</code>，<code>user.verify(userToken)</code>非空。
     */
    @Test
    void providerKeepsSameLoginIdInTwoRealmsIsolated() {
        SaTokenSubjectProvider provider = new SaTokenSubjectProvider();
        Subject admin = provider.getSubject("admin");
        Subject user = provider.getSubject("user");

        String adminToken = admin.login(AuthRequest.of("shared-id")
                .setPrincipal(principal("shared-id").setUserId("admin")));
        String userToken = user.login(AuthRequest.of("shared-id")
                .setPrincipal(principal("shared-id").setUserId("user")));

        assertEquals("admin", admin.verify(adminToken).getUserId());
        assertEquals("user", user.verify(userToken).getUserId());
        assertNull(admin.verify(userToken));
        assertNull(user.verify(adminToken));
        SubjectKit.register(provider);
        SubjectKit.setDataProvider(new SubjectDataProvider() {
            @Override
            public List<String> getPermissionList(AuthPrincipal principal) {
                return List.of(principal.getUserId() + ":read");
            }
        });
        SaTokenSubjectDataBridge bridge = new SaTokenSubjectDataBridge();
        assertEquals(List.of("admin:read"), bridge.getPermissionList("shared-id", "admin"));
        assertEquals(List.of("user:read"), bridge.getPermissionList("shared-id", "user"));
        admin.logout("shared-id");
        assertNull(admin.verify(adminToken));
        assertNotNull(user.verify(userToken));
    }

    /**
     * 验证<code>subject().verify(oldToken)</code>为空，<code>subject().verify(newToken)</code>非空。
     */
    @Test
    void loginAppliesReplacementAcrossDeviceTypes() {
        AuthSessionConfig oldDevice = new AuthSessionConfig().setDeviceType("desktop");
        String oldToken = subject().login(AuthRequest.of("replace-device")
                .setSessionConfig(oldDevice).setPrincipal(principal("replace-device")));

        AuthSessionConfig newDevice = new AuthSessionConfig().setDeviceType("mobile").setConcurrent(false)
                .setReplacedLoginExitMode(AuthReplacedLoginExitMode.NEW_DEVICE)
                .setReplacedRange(AuthReplacedRange.ALL_DEVICE_TYPE);
        String newToken = subject().login(AuthRequest.of("replace-device")
                .setSessionConfig(newDevice).setPrincipal(principal("replace-device")));

        assertNull(subject().verify(oldToken));
        assertNotNull(subject().verify(newToken));
    }

    /**
     * 验证<code>subject().refresh()</code> 抛出 <code>NotLoggedInException.class</code>，<code>subject().getPrincipalByLoginId("refresh-missing-terminal")</code>为空。
     */
    @Test
    void refreshRejectsMissingTerminalWithoutCreatingSession() {
        login("refresh-missing-terminal");
        SaManager.getSaTokenDao().deleteSession(StpUtil.stpLogic.splicingKeySession("refresh-missing-terminal"));

        assertThrows(NotLoggedInException.class, () -> subject().refresh());
        assertNull(subject().getPrincipalByLoginId("refresh-missing-terminal"));
    }

    /**
     * 验证<code>subject().refresh()</code> 抛出 <code>NotLoggedInException.class</code>，<code>SaManager.getSaTokenDao().get(StpUtil.stpLogic.splicingKeyTokenValue(token))</code>为空，<code>SaManager.getSaTokenDao().get(activityKey)</code>为空。
     */
    @Test
    void frozenTokenCannotRefreshAndCanStillBeRevoked() {
        SaManager.getConfig().setActiveTimeout(60);
        String token = login("refresh-frozen");
        String activityKey = StpUtil.stpLogic.splicingKeyLastActiveTime(token);
        SaManager.getSaTokenDao().update(activityKey, "1");

        assertThrows(NotLoggedInException.class, () -> subject().refresh());
        subject().logout();
        assertNull(SaManager.getSaTokenDao().get(StpUtil.stpLogic.splicingKeyTokenValue(token)));
        assertNull(SaManager.getSaTokenDao().get(activityKey));
    }

    /**
     * 验证<code>subject().verify("")</code>为空，<code>subject().verify(null)</code>为空。
     */
    @Test
    void verifyRejectsBlankToken() {
        assertNull(subject().verify(""));
        assertNull(subject().verify(null));
    }

    /**
     * 验证<code>saSubject.isPermitted("order:read")</code>为真，<code>saSubject.isPermitted("order:delete")</code>为假，<code>saSubject.isPermittedAll("order:read", "order:delete")</code>为假。
     */
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

    /**
     * 验证<code>saSubject.isPermitted("anything")</code>为假，<code>saSubject.isPermitted("nobody", "anything")</code>为假，<code>saSubject.isPermittedAll("user-9")</code>为假。
     */
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

    /**
     * 验证<code>saSubject.hasRole("admin")</code>为真，<code>saSubject.hasRole("viewer")</code>为假，<code>saSubject.hasAllRole("admin", "viewer")</code>为假。
     */
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

    /**
     * 验证<code>saSubject.hasRole("admin")</code>为假，<code>saSubject.hasRole("nobody", "admin")</code>为假，<code>saSubject.hasAllRole("user-10")</code>为假。
     */
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

    /**
     * 验证<code>saSubject.isAuthenticated()</code>为真，<code>saSubject.isAuthenticated("user-11")</code>为真，<code>saSubject.getRoleId()</code> 等于 <code>"role-1"</code>。
     */
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

    /**
     * 验证<code>saSubject.isAuthenticated()</code>为假，<code>saSubject.isAuthenticated("nobody")</code>为假，<code>saSubject.getRoleId()</code>为空。
     */
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

    /**
     * 验证<code>subject().getExtra(token, "channel")</code> 等于 <code>"mobile"</code>。
     */
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

    /**
     * 验证<code>saSubject.isDisabled("user-13")</code>为假，<code>saSubject.isDisabled("user-13")</code>为真。
     */
    @Test
    void disableLifecycleDelegatesToSaToken() {
        SaTokenSubject saSubject = subject();

        assertFalse(saSubject.isDisabled("user-13"));
        saSubject.disable("user-13", 3600);
        assertTrue(saSubject.isDisabled("user-13"));
        saSubject.untieDisable("user-13");
        assertFalse(saSubject.isDisabled("user-13"));
    }

    /**
     * 验证<code>token</code>非空，<code>SaManager.getStpLogic("admin").isLogin("admin-1")</code>为真。
     */
    @Test
    void loginToCustomRealmResolvesStpLogic() {
        SaTokenSubject saSubject = subject();
        AuthRequest request = AuthRequest.of("admin-1").setRealm("admin")
                .setPrincipal(principal("admin-1"));

        String token = saSubject.login(request);

        assertNotNull(token);
        assertTrue(SaManager.getStpLogic("admin").isLogin("admin-1"));
    }

    /**
     * 验证<code>token</code>非空。
     */
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

    /**
     * 验证<code>subject().verify("no-such-token")</code>为空。
     */
    @Test
    void verifyHandlesSessionAndJwtMismatchSafely() {
        login("user-15");
        Map<String, Object> extras = new HashMap<>();
        extras.put("channel", "web");

        // 非 JWT 模式下传入任意 token 应被拒
        assertNull(subject().verify("no-such-token"));
    }

    /**
     * 验证<code>token</code>非空。
     */
    @Test
    void loginMapsReplacedOverflowMode() {
        AuthSessionConfig sessionConfig = new AuthSessionConfig();
        sessionConfig.setOverflowLogoutMode(AuthLogoutMode.REPLACED);
        AuthRequest request = AuthRequest.of("user-16").setPrincipal(principal("user-16"))
                .setSessionConfig(sessionConfig);

        String token = subject().login(request);

        assertNotNull(token);
    }

    /**
     * 验证<code>token</code>非空。
     */
    @Test
    void loginAcceptsNullSessionConfig() {
        AuthRequest request = AuthRequest.of("user-17").setPrincipal(principal("user-17"));
        request.setSessionConfig(null);

        String token = subject().login(request);

        assertNotNull(token);
    }

    /**
     * 验证<code>subject().getPrincipal()</code>为空。
     */
    @Test
    void getPrincipalReturnsNullWhenSessionVanished() {
        String loginId = "user-18";
        login(loginId);
        SaManager.getSaTokenDao().delete(StpUtil.stpLogic.splicingKeySession(loginId));

        assertNull(subject().getPrincipal());
    }

    /**
     * 验证<code>saSubject.isPermitted("nobody", new String[]{"order:read"}).length</code> 等于 <code>0</code>，<code>saSubject.isPermitted("nobody", (String[]) null).length</code> 等于 <code>0</code>，<code>saSubject.hasAllRole("nobody", (String[]) null)</code>为假。
     */
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

    /**
     * 验证<code>saSubject.isTrustDeviceId("device-9")</code>为假，<code>saSubject.isTrustDeviceId(1001L, "device-9")</code>为假。
     */
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
