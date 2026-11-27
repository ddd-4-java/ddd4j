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

/** \u9a8c\u8bc1 <code>ShiroSubject</code> \u5728<code>defaults</code>\u3001<code>login</code>\u3001<code>works</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class ShiroSubjectTest {

    /**
     * \u521b\u5efa <code>new ShiroSubject</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new ShiroSubject()</code>
     */
    private ShiroSubject shiroSubject() {
        return new ShiroSubject();
    }

    /**
     * \u521b\u5efa <code>new AuthPrincipal</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param loginId \u6d4b\u8bd5\u4f1a\u8bdd\u4f7f\u7528\u7684\u8d26\u53f7\u6807\u8bc6
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new AuthPrincipal().setLoginId(loginId).setUserId("user-" + loginId).setOrgId("org-1").setRoleId("role-1")</code>
     */
    private AuthPrincipal principal(String loginId) {
        return new AuthPrincipal().setLoginId(loginId).setUserId("user-" + loginId)
                .setOrgId("org-1").setRoleId("role-1");
    }

    /**
     * \u521b\u5efa <code>new AuthRequest</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param loginId \u6d4b\u8bd5\u4f1a\u8bdd\u4f7f\u7528\u7684\u8d26\u53f7\u6807\u8bc6
     * @param credential \u4f20\u7ed9 <code>new AuthRequest</code> \u7684 <code>String</code> \u8f93\u5165
     * @param principal \u4f20\u7ed9 <code>new AuthRequest</code> \u7684 <code>AuthPrincipal</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new AuthRequest(loginId).setPrincipal(principal).extra("credential", credential)</code>
     */
    private AuthRequest request(String loginId, String credential, AuthPrincipal principal) {
        return new AuthRequest(loginId).setPrincipal(principal)
                .extra("credential", credential);
    }

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>org.apache.shiro.util.ThreadContext.remove()</code>\u3001<code>manager.setRealm()</code>\u3001<code>SecurityUtils.setSecurityManager()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUp() {
        org.apache.shiro.util.ThreadContext.remove();
        DefaultSecurityManager manager = new DefaultSecurityManager();
        manager.setRealm(new TestRealm());
        SecurityUtils.setSecurityManager(manager);
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>SecurityUtils.setSecurityManager()</code>\u3001<code>org.apache.shiro.util.ThreadContext.remove()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        SecurityUtils.setSecurityManager(null);
        org.apache.shiro.util.ThreadContext.remove();
    }

    /** \u9a8c\u8bc1<code>subject.getPrincipal()</code>\u4e3a\u7a7a\uff0c<code>subject.getPrincipalByLoginId("anyone")</code>\u4e3a\u7a7a\uff0c<code>subject.isDisabled("anyone")</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>sessionId</code>\u975e\u7a7a\uff0c<code>subject.isAuthenticated()</code>\u4e3a\u771f\uff0c<code>subject.isAuthenticated("other")</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>sessionId</code>\u975e\u7a7a\uff0c<code>subject.isAuthenticated()</code>\u4e3a\u771f\uff0c<code>subject.getPrincipal()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void loginWithEmptyPrincipalKeepsShiroFallback() {
        ShiroSubject subject = shiroSubject();

        String sessionId = subject.login(request("user-1", "pw", null));

        assertNotNull(sessionId);
        assertTrue(subject.isAuthenticated());
        // session 中无 AuthPrincipal，回退到 Shiro principal（String）→ null
        assertNull(subject.getPrincipal());
    }

    /** \u9a8c\u8bc1<code>sessionId</code>\u975e\u7a7a\uff0c<code>session.getTimeout()</code> \u7b49\u4e8e <code>60_000L</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>subject.login(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>subject.login(noLoginId)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void loginRejectsNullArguments() {
        ShiroSubject subject = shiroSubject();

        assertThrows(NullPointerException.class, () -> subject.login(null));
        AuthRequest noLoginId = new AuthRequest();
        assertThrows(NullPointerException.class, () -> subject.login(noLoginId));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>UnknownAccountException.class</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>BadCredentialsException.class</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NotLoggedInException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>subject.isAuthenticated()</code>\u4e3a\u771f\uff0c<code>subject.isAuthenticated()</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>refreshed</code> \u7b49\u4e8e <code>sessionId</code>\u3002 */
    @Test
    void refreshTouchesSession() {
        ShiroSubject subject = shiroSubject();
        String sessionId = subject.login(request("user-1", "pw", principal("user-1")));

        String refreshed = subject.refresh();

        assertEquals(sessionId, refreshed);
    }

    /** \u9a8c\u8bc1<code>subject.getExtra("ignored-token", "channel")</code> \u7b49\u4e8e <code>"web"</code>\uff0c<code>subject.getExtra("ignored-token", "missing")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void getExtraReadsSessionAttributes() {
        ShiroSubject subject = shiroSubject();
        subject.login(request("user-1", "pw", principal("user-1")));
        SecurityUtils.getSubject().getSession(true).setAttribute("channel", "web");

        assertEquals("web", subject.getExtra("ignored-token", "channel"));
        assertNull(subject.getExtra("ignored-token", "missing"));
    }

    /** \u9a8c\u8bc1<code>subject.isPermitted("order:read")</code>\u4e3a\u771f\uff0c<code>subject.isPermitted("order:delete")</code>\u4e3a\u5047\uff0c<code>subject.hasAllRole("user-1", new String[]{"admin"})</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>subject.isPermitted().length</code> \u7b49\u4e8e <code>0</code>\uff0c<code>subject.hasRoles().length</code> \u7b49\u4e8e <code>0</code>\uff0c<code>subject.hasAllRole()</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>subject.getPrincipal()</code>\u4e3a\u7a7a\uff0c<code>subject.isPermitted("anything")</code>\u4e3a\u5047\uff0c<code>subject.refresh()</code>\u4e3a\u7a7a\u3002 */
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
         * \u521b\u5efa <code>new SimpleAuthenticationInfo</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
         * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new SimpleAuthenticationInfo(username, "pw", getName())</code>
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
         * \u8bfb\u53d6 <code>info</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param principals \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>PrincipalCollection</code> \u53c2\u6570 <code>principals</code>
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>info</code>
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
