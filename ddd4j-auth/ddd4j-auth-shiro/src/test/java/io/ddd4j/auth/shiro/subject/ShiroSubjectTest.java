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
package io.ddd4j.auth.shiro.subject;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.exception.*;
import io.ddd4j.core.exception.UnknownAccountException;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 <code>ShiroSubject</code> 在<code>defaults</code>、<code>login</code>、<code>works</code>等操作中的行为契约。
 */
class ShiroSubjectTest {

    /**
     * 创建 <code>new ShiroSubject</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new ShiroSubject()</code>
     */
    private ShiroSubject shiroSubject() {
        return new ShiroSubject();
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
     * 创建 <code>new AuthRequest</code> 供当前测试路径调用。
     *
     * @param loginId    测试会话使用的账号标识
     * @param credential 传给 <code>new AuthRequest</code> 的 <code>String</code> 输入
     * @param principal  传给 <code>new AuthRequest</code> 的 <code>AuthPrincipal</code> 输入
     * @return 当前测试使用的结果，取自 <code>new AuthRequest(loginId).setPrincipal(principal).extra("credential", credential)</code>
     */
    private AuthRequest request(String loginId, String credential, AuthPrincipal principal) {
        return new AuthRequest(loginId).setPrincipal(principal)
                .extra("credential", credential);
    }

    /**
     * 测试执行前执行 <code>org.apache.shiro.util.ThreadContext.remove()</code>、<code>manager.setRealm()</code>、<code>SecurityUtils.setSecurityManager()</code> 完成资源准备。
     */
    @BeforeEach
    void setUp() {
        org.apache.shiro.util.ThreadContext.remove();
        DefaultSecurityManager manager = new DefaultSecurityManager();
        manager.setRealm(new TestRealm());
        SecurityUtils.setSecurityManager(manager);
    }

    /**
     * 测试结束后执行 <code>SecurityUtils.setSecurityManager()</code>、<code>org.apache.shiro.util.ThreadContext.remove()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        SecurityUtils.setSecurityManager(null);
        org.apache.shiro.util.ThreadContext.remove();
    }

    /**
     * 验证<code>subject.getPrincipal()</code>为空，<code>subject.getPrincipalByLoginId("anyone")</code>为空，<code>subject.isDisabled("anyone")</code>为假。
     */
    @Test
    void defaultsWhenNotLoggedIn() {
        ShiroSubject subject = shiroSubject();

        assertNull(subject.getPrincipal());
        assertNull(subject.getPrincipalByLoginId("anyone"));
        assertNull(subject.getPrincipalByLoginId(null));
        assertNull(subject.getPrincipalByToken("token"));
        assertNull(subject.verify("token"));
        assertFalse(subject.isPermitted("anything"));
        assertFalse(subject.isPermitted("anyone", "anything"));
        assertEquals(0, subject.isPermitted().length);
        assertArrayEquals(new boolean[]{false},
                subject.isPermitted("anyone", new String[]{"a"}));
        assertFalse(subject.isPermittedAll("a"));
        assertFalse(subject.isPermittedAll("anyone", new String[]{"a"}));
        assertFalse(subject.isPermittedAny("a"));
        assertFalse(subject.isPermittedAny("anyone", new String[]{"a"}));
        assertFalse(subject.hasRole("admin"));
        assertFalse(subject.hasRole("anyone", "admin"));
        assertEquals(0, subject.hasRoles().length);
        assertArrayEquals(new boolean[]{false},
                subject.hasRoles("anyone", new String[]{"admin"}));
        assertFalse(subject.hasAnyRole("admin"));
        assertFalse(subject.hasAnyRole("anyone", new String[]{"admin"}));
        assertFalse(subject.hasAllRole("admin"));
        assertFalse(subject.hasAllRole("anyone", new String[]{"admin"}));
        assertFalse(subject.isAuthenticated());
        assertFalse(subject.isAuthenticated("anyone"));
        assertFalse(subject.isRemembered());
        assertFalse(subject.isTrustDeviceId("device-1"));
        assertFalse(subject.isTrustDeviceId(1001L, "device-1"));
        assertNull(subject.getLoginId());
        assertNull(subject.getUserId());
        assertNull(subject.getOrgId());
        assertNull(subject.getRoleId());
        assertNull(subject.getExtra("token", "key"));
        assertNull(subject.refresh());
        subject.logout();
        subject.logout("anyone");
        subject.kickout(null);
        subject.disable("anyone", 60);
        assertFalse(subject.isDisabled("anyone"));
        subject.untieDisable("anyone");
    }

    /**
     * 验证<code>sessionId</code>非空，<code>subject.isAuthenticated()</code>为真，<code>subject.isAuthenticated("other")</code>为假。
     */
    @Test
    void loginStoresPrincipalAndSessionConfig() {
        ShiroSubject subject = shiroSubject();
        AuthPrincipal principal = principal("user-1");

        String sessionId = subject.login(request("user-1", "pw", principal));

        assertNotNull(sessionId);
        assertTrue(subject.isAuthenticated());
        assertSame(principal, subject.getPrincipal());
        assertEquals("user-1", subject.getLoginId());
        assertEquals("user-user-1", subject.getUserId());
        assertEquals("org-1", subject.getOrgId());
        assertEquals("role-1", subject.getRoleId());
        assertEquals("user-1", subject.getPrincipalByLoginId("user-1").getLoginId());
        assertNull(subject.getPrincipalByLoginId("other"));
        assertEquals("user-1", subject.getPrincipalByToken("anything").getLoginId());
        assertEquals("user-1", subject.verify("anything").getLoginId());
        assertTrue(subject.isAuthenticated("user-1"));
        assertFalse(subject.isAuthenticated("other"));
    }

    /**
     * 验证<code>sessionId</code>非空，<code>subject.isAuthenticated()</code>为真，<code>subject.getPrincipal()</code>为空。
     */
    @Test
    void loginWithEmptyPrincipalKeepsShiroFallback() {
        ShiroSubject subject = shiroSubject();

        String sessionId = subject.login(request("user-1", "pw", null));

        assertNotNull(sessionId);
        assertTrue(subject.isAuthenticated());
        // session 中无 AuthPrincipal，回退到 Shiro principal（String）→ null
        assertNull(subject.getPrincipal());
    }

    /**
     * 验证<code>sessionId</code>非空，<code>session.getTimeout()</code> 等于 <code>60_000L</code>。
     */
    @Test
    void loginAppliesSessionTimeout() {
        io.ddd4j.core.auth.session.AuthSessionConfig sessionConfig =
                new io.ddd4j.core.auth.session.AuthSessionConfig();
        sessionConfig.setTimeout(60);
        AuthRequest request = request("user-1", "pw", principal("user-1"));
        request.setSessionConfig(sessionConfig);

        String sessionId = shiroSubject().login(request);

        assertNotNull(sessionId);
        org.apache.shiro.session.Session session = SecurityUtils.getSubject().getSession(false);
        assertEquals(60_000L, session.getTimeout());
    }

    /**
     * 验证<code>subject.login(null)</code> 抛出 <code>NullPointerException.class</code>，<code>subject.login(noLoginId)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void loginRejectsNullArguments() {
        ShiroSubject subject = shiroSubject();

        assertThrows(NullPointerException.class, () -> subject.login(null));
        AuthRequest noLoginId = new AuthRequest();
        assertThrows(NullPointerException.class, () -> subject.login(noLoginId));
    }

    /**
     * 验证被测调用 抛出 <code>UnknownAccountException.class</code>，被测调用 抛出 <code>BadCredentialsException.class</code>，被测调用 抛出 <code>NotLoggedInException.class</code>。
     */
    @Test
    void loginMapsShiroAuthenticationFailures() {
        ShiroSubject subject = shiroSubject();

        assertThrows(UnknownAccountException.class,
                () -> subject.login(request("missing", "pw", principal("missing"))));
        assertThrows(BadCredentialsException.class,
                () -> subject.login(request("user-1", "wrong", principal("user-1"))));
        assertThrows(AccountLockedException.class,
                () -> subject.login(request("locked", "pw", principal("locked"))));
        assertThrows(AccountDisabledException.class,
                () -> subject.login(request("disabled", "pw", principal("disabled"))));
        assertThrows(CredentialsExpiredException.class,
                () -> subject.login(request("expired", "pw", principal("expired"))));
        assertThrows(NotLoggedInException.class,
                () -> subject.login(request("runtime", "pw", principal("runtime"))));
    }

    /**
     * 验证<code>subject.isAuthenticated()</code>为真，<code>subject.isAuthenticated()</code>为假。
     */
    @Test
    void logoutAndKickoutCurrentUser() {
        ShiroSubject subject = shiroSubject();
        subject.login(request("user-1", "pw", principal("user-1")));
        assertTrue(subject.isAuthenticated());

        subject.logout("user-1");
        assertFalse(subject.isAuthenticated());

        subject.login(request("user-1", "pw", principal("user-1")));
        subject.kickout("user-1");
        assertFalse(subject.isAuthenticated());

        subject.login(request("user-1", "pw", principal("user-1")));
        subject.kickout("other");
        assertTrue(subject.isAuthenticated());
    }

    /**
     * 验证<code>refreshed</code> 等于 <code>sessionId</code>。
     */
    @Test
    void refreshTouchesSession() {
        ShiroSubject subject = shiroSubject();
        String sessionId = subject.login(request("user-1", "pw", principal("user-1")));

        String refreshed = subject.refresh();

        assertEquals(sessionId, refreshed);
    }

    /**
     * 验证<code>subject.getExtra("ignored-token", "channel")</code> 等于 <code>"web"</code>，<code>subject.getExtra("ignored-token", "missing")</code>为空。
     */
    @Test
    void getExtraReadsSessionAttributes() {
        ShiroSubject subject = shiroSubject();
        subject.login(request("user-1", "pw", principal("user-1")));
        SecurityUtils.getSubject().getSession(true).setAttribute("channel", "web");

        assertEquals("web", subject.getExtra("ignored-token", "channel"));
        assertNull(subject.getExtra("ignored-token", "missing"));
    }

    /**
     * 验证<code>subject.isPermitted("order:read")</code>为真，<code>subject.isPermitted("order:delete")</code>为假，<code>subject.hasAllRole("user-1", new String[]{"admin"})</code>为真。
     */
    @Test
    void permissionsAndRolesDelegateToAuthorizer() {
        ShiroSubject subject = shiroSubject();
        subject.login(request("user-1", "pw", principal("user-1")));

        assertTrue(subject.isPermitted("order:read"));
        assertFalse(subject.isPermitted("order:delete"));
        assertTrue(subject.isPermitted("user-1", "order:read"));
        assertArrayEquals(new boolean[]{true, false},
                subject.isPermitted(new String[]{"order:read", "order:delete"}));
        assertArrayEquals(new boolean[]{true, false},
                subject.isPermitted("user-1", new String[]{"order:read", "order:delete"}));
        assertTrue(subject.isPermittedAll("order:read"));
        assertFalse(subject.isPermittedAll(new String[]{"order:read", "order:delete"}));
        assertTrue(subject.isPermittedAll("user-1", new String[]{"order:read"}));
        assertTrue(subject.isPermittedAny(new String[]{"order:delete", "order:read"}));
        assertFalse(subject.isPermittedAny("order:delete"));
        assertTrue(subject.isPermittedAny("user-1", new String[]{"order:delete", "order:read"}));

        assertTrue(subject.hasRole("admin"));
        assertFalse(subject.hasRole("viewer"));
        assertTrue(subject.hasRole("user-1", "admin"));
        assertArrayEquals(new boolean[]{true, false},
                subject.hasRoles(new String[]{"admin", "viewer"}));
        assertArrayEquals(new boolean[]{true, false},
                subject.hasRoles("user-1", new String[]{"admin", "viewer"}));
        assertTrue(subject.hasAnyRole(new String[]{"viewer", "admin"}));
        assertFalse(subject.hasAnyRole("viewer"));
        assertTrue(subject.hasAnyRole("user-1", new String[]{"viewer", "admin"}));
        assertTrue(subject.hasAllRole("admin"));
        assertFalse(subject.hasAllRole(new String[]{"admin", "viewer"}));
        assertTrue(subject.hasAllRole("user-1", new String[]{"admin"}));
    }

    /**
     * 验证<code>subject.isPermitted().length</code> 等于 <code>0</code>，<code>subject.hasRoles().length</code> 等于 <code>0</code>，<code>subject.hasAllRole()</code>为假。
     */
    @Test
    void permissionDefaultsForEmptyArrays() {
        ShiroSubject subject = shiroSubject();
        subject.login(request("user-1", "pw", principal("user-1")));

        assertEquals(0, subject.isPermitted().length);
        assertEquals(0, subject.hasRoles().length);
        assertFalse(subject.isPermittedAll());
        assertFalse(subject.isPermittedAny());
        assertFalse(subject.hasAnyRole());
        assertFalse(subject.hasAllRole());
    }

    /**
     * 验证<code>subject.getPrincipal()</code>为空，<code>subject.isPermitted("anything")</code>为假，<code>subject.refresh()</code>为空。
     */
    @Test
    void worksWithoutSecurityManager() {
        SecurityUtils.setSecurityManager(null);
        ShiroSubject subject = shiroSubject();

        assertNull(subject.getPrincipal());
        assertFalse(subject.isPermitted("anything"));
        assertEquals(0, subject.isPermitted(new String[]{"a", "b"}).length);
        assertFalse(subject.isPermittedAll("a"));
        assertFalse(subject.isPermittedAny("a"));
        assertFalse(subject.hasRole("admin"));
        assertEquals(0, subject.hasRoles("admin").length);
        assertFalse(subject.hasAnyRole("admin"));
        assertFalse(subject.hasAllRole("admin"));
        assertFalse(subject.isAuthenticated());
        assertNull(subject.getExtra("t", "k"));
        assertNull(subject.refresh());
        subject.logout();
    }

    /**
     * 测试 Realm：固定用户/密码，admin 角色与 order:read 权限。
     */
    private static final class TestRealm extends AuthorizingRealm {

        /**
         * 创建 <code>new SimpleAuthenticationInfo</code> 供当前测试路径调用。
         *
         * @param token 从测试登录获得的会话凭据
         * @return 当前测试使用的结果，取自 <code>new SimpleAuthenticationInfo(username, "pw", getName())</code>
         */
        @Override
        protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
            String username = (String) token.getPrincipal();
            if ("missing".equals(username)) {
                throw new org.apache.shiro.authc.UnknownAccountException("no such user");
            }
            if ("locked".equals(username)) {
                throw new LockedAccountException("locked");
            }
            if ("disabled".equals(username)) {
                throw new DisabledAccountException("disabled");
            }
            if ("expired".equals(username)) {
                throw new org.apache.shiro.authc.ExpiredCredentialsException("expired");
            }
            if ("runtime".equals(username)) {
                throw new IllegalStateException("runtime failure");
            }
            return new SimpleAuthenticationInfo(username, "pw", getName());
        }

        /**
         * 读取 <code>info</code> 供当前测试使用。
         *
         * @param principals 本次调用使用的 <code>PrincipalCollection</code> 参数 <code>principals</code>
         * @return 当前测试使用的结果，取自 <code>info</code>
         */
        @Override
        protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
            SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
            info.addRole("admin");
            info.addStringPermissions(Collections.singletonList("order:read"));
            return info;
        }
    }
}
