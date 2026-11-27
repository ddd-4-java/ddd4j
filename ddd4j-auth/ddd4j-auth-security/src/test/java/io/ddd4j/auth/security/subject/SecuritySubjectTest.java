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
package io.ddd4j.auth.security.subject;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.util.SubjectKit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 Spring Security Subject 的身份解析与上下文生命周期。
 */
class SecuritySubjectTest {

    /** \u8fdb\u5165\u5f53\u524d\u6267\u884c\u8303\u56f4\u524d\u4fdd\u5b58\u7684\u7ec4\u4ef6\uff0c\u9000\u51fa\u65f6\u7528\u4e8e\u6062\u590d <code>SubjectDataProvider</code>\u3002 */
    private SubjectDataProvider originalDataProvider;

    /**
     * \u521b\u5efa <code>new SecuritySubject</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new SecuritySubject()</code>
     */
    private SecuritySubject subject() {
        return new SecuritySubject();
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

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u6e05\u9664\u4e0a\u4e0b\u6587<code>org.springframework.security.core.context.SecurityContextHolder</code>\u3002 */
    @BeforeEach
    void setUp() {
        originalDataProvider = SubjectKit.dataProvider;
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6e05\u9664\u4e0a\u4e0b\u6587<code>org.springframework.security.core.context.SecurityContextHolder</code>\u3002 */
    @AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        SubjectKit.setDataProvider(originalDataProvider);
    }

    /**
     * \u8c03\u7528 <code>org.springframework.security.core.context.SecurityContextHolder.getContext()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param auth \u4f20\u7ed9 <code>org.springframework.security.core.context.SecurityContextHolder.getContext()</code> \u7684 <code>org.springframework.security.core.Authentication</code> \u8f93\u5165
     */
    private void authenticate(org.springframework.security.core.Authentication auth) {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(auth);
    }

    /** \u9a8c\u8bc1<code>subject.getPrincipal()</code>\u4e3a\u7a7a\uff0c<code>subject.getPrincipalByLoginId("anyone")</code>\u4e3a\u7a7a\uff0c<code>subject.isDisabled("anyone")</code>\u4e3a\u5047\u3002 */
    @Test
    void defaultsWhenNotLoggedIn() {
        SecuritySubject subject = subject();

        assertNull(subject.getPrincipal());
        assertNull(subject.getPrincipalByLoginId("anyone"));
        assertNull(subject.getPrincipalByToken("token"));
        assertNull(subject.verify("token"));
        assertNull(subject.refresh());
        assertFalse(subject.isPermitted("anything"));
        assertFalse(subject.isPermitted("anyone", "anything"));
        assertEquals(0, subject.isPermitted().length);
        assertEquals(0, subject.isPermitted("anyone", new String[]{"a"}).length);
        assertFalse(subject.isPermittedAll("a"));
        assertFalse(subject.isPermittedAll("anyone", new String[]{"a"}));
        assertFalse(subject.isPermittedAny("a"));
        assertFalse(subject.isPermittedAny("anyone", new String[]{"a"}));
        assertFalse(subject.hasRole("admin"));
        assertFalse(subject.hasRole("anyone", "admin"));
        assertEquals(0, subject.hasRoles().length);
        assertEquals(0, subject.hasRoles("anyone", new String[]{"admin"}).length);
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
        assertNull(subject.getExtra("t", "k"));
        assertFalse(subject.isDisabled("anyone"));
        subject.logout();
        subject.logout("anyone");
        subject.kickout("anyone");
        subject.disable("anyone", 60);
        subject.untieDisable("anyone");
    }

    /** \u9a8c\u8bc1<code>subject.getPrincipal()</code>\u4e3a\u7a7a\uff0c<code>subject.isAuthenticated()</code>\u4e3a\u5047\uff0c<code>subject.isPermitted("anything")</code>\u4e3a\u5047\u3002 */
    @Test
    void anonymousTokenIsNotAuthenticated() {
        authenticate(new AnonymousAuthenticationToken("key", "guest",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_GUEST"))));
        SecuritySubject subject = subject();

        assertNull(subject.getPrincipal());
        assertFalse(subject.isAuthenticated());
        assertNull(subject.getLoginId());
        assertFalse(subject.isPermitted("anything"));
    }

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>"user-1"</code>\uff0c<code>subject.isAuthenticated()</code>\u4e3a\u771f\uff0c<code>subject.isAuthenticated("user-1")</code>\u4e3a\u771f\u3002 */
    @Test
    void loginStoresAuthPrincipalAndReturnsLoginId() {
        SecuritySubject subject = subject();
        AuthPrincipal principal = principal("user-1");

        String result = subject.login(new AuthRequest("user-1").setPrincipal(principal));

        assertEquals("user-1", result);
        assertTrue(subject.isAuthenticated());
        assertSame(principal, subject.getPrincipal());
        assertNotNull(subject.getLoginId());
        assertEquals("user-user-1", subject.getUserId());
        assertEquals("user-1", subject.getPrincipalByLoginId("anyone").getLoginId());
        assertEquals("user-1", subject.getPrincipalByToken("any-token").getLoginId());
        assertEquals("user-1", subject.verify("any-token").getLoginId());
        assertTrue(subject.isAuthenticated("user-1"));
    }

    /** \u9a8c\u8bc1<code>principal</code>\u975e\u7a7a\uff0c<code>principal.getLoginId()</code> \u7b49\u4e8e <code>"user-2"</code>\uff0c<code>principal.getUserCode()</code> \u7b49\u4e8e <code>"user-2"</code>\u3002 */
    @Test
    void loginWithoutPrincipalAdaptsFromName() {
        SecuritySubject subject = subject();
        subject.login(new AuthRequest("user-2"));

        AuthPrincipal principal = subject.getPrincipal();

        assertNotNull(principal);
        assertEquals("user-2", principal.getLoginId());
        assertEquals("user-2", principal.getUserId());
        assertEquals("user-2", principal.getUserCode());
    }

    /** \u9a8c\u8bc1<code>subject().getPrincipal()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>principal</code>\u3002 */
    @Test
    void principalFromAuthUserDetailsDelegate() {
        AuthPrincipal principal = principal("user-3");
        AuthUserDetails details = new AuthUserDetails("user-3", "pw", true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_admin")), principal);
        authenticate(new UsernamePasswordAuthenticationToken(details, "pw",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_admin"))));

        assertSame(principal, subject().getPrincipal());
    }

    /** \u9a8c\u8bc1<code>principal.getLoginId()</code> \u7b49\u4e8e <code>"user-4"</code>\uff0c<code>principal.getRoleCode()</code> \u7b49\u4e8e <code>"admin"</code>\uff0c<code>principal.getRoles().get(1).getRoleCode()</code> \u7b49\u4e8e <code>"editor"</code>\u3002 */
    @Test
    void principalAdaptsRolesFromAuthorities() {
        authenticate(new UsernamePasswordAuthenticationToken("user-4", "pw",
                Arrays.asList(new SimpleGrantedAuthority("ROLE_admin"),
                        new SimpleGrantedAuthority("ROLE_editor"),
                        new SimpleGrantedAuthority("READ"))));

        AuthPrincipal principal = subject().getPrincipal();

        assertEquals("user-4", principal.getLoginId());
        assertEquals("admin", principal.getRoleCode());
        assertEquals(2, principal.getRoles().size());
        assertEquals("editor", principal.getRoles().get(1).getRoleCode());
    }

    /** \u9a8c\u8bc1<code>subject.isAuthenticated()</code>\u4e3a\u771f\uff0c<code>subject.isRemembered()</code>\u4e3a\u771f\uff0c<code>subject.getPrincipal().getLoginId()</code> \u7b49\u4e8e <code>"user-5"</code>\u3002 */
    @Test
    void rememberMeTokenIsRemembered() {
        authenticate(new RememberMeAuthenticationToken("key", principal("user-5"),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_admin"))));

        SecuritySubject subject = subject();
        assertTrue(subject.isAuthenticated());
        assertTrue(subject.isRemembered());
        assertEquals("user-5", subject.getPrincipal().getLoginId());
    }

    /** \u9a8c\u8bc1<code>subject.isAuthenticated()</code>\u4e3a\u771f\uff0c<code>subject.isAuthenticated()</code>\u4e3a\u5047\u3002 */
    @Test
    void logoutAndKickoutClearContext() {
        SecuritySubject subject = subject();
        subject.login(new AuthRequest("user-6"));
        assertTrue(subject.isAuthenticated());

        subject.logout("user-6");
        assertFalse(subject.isAuthenticated());

        subject.login(new AuthRequest("user-6"));
        subject.kickout("user-6");
        assertFalse(subject.isAuthenticated());
    }

    /** \u9a8c\u8bc1<code>subject.refresh()</code> \u7b49\u4e8e <code>"user-7"</code>\u3002 */
    @Test
    void refreshReturnsCurrentName() {
        SecuritySubject subject = subject();
        subject.login(new AuthRequest("user-7"));

        assertEquals("user-7", subject.refresh());
    }

    /** \u9a8c\u8bc1<code>subject.getExtra("ignored-token", "channel")</code> \u7b49\u4e8e <code>"web"</code>\uff0c<code>subject.getExtra("ignored-token", "missing")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void getExtraReadsPrincipalProfile() {
        SecuritySubject subject = subject();
        AuthPrincipal principal = principal("user-8");
        principal.getProfile().put("channel", "web");
        subject.login(new AuthRequest("user-8").setPrincipal(principal));

        assertEquals("web", subject.getExtra("ignored-token", "channel"));
        assertNull(subject.getExtra("ignored-token", "missing"));
    }

    /** \u9a8c\u8bc1<code>subject.isPermitted("order:read")</code>\u4e3a\u771f\uff0c<code>subject.isPermitted("order:delete")</code>\u4e3a\u5047\uff0c<code>subject.isPermittedAny()</code>\u4e3a\u5047\u3002 */
    @Test
    void permissionMatrixReflectsDataProvider() {
        SecuritySubject subject = subject();
        subject.login(new AuthRequest("user-9").setPrincipal(principal("user-9")));
        AtomicReference<List<String>> permissions =
                new AtomicReference<>(Arrays.asList("order:read", "user:write"));
        SubjectKit.setDataProvider(new SubjectDataProvider() {
            @Override
            public List<String> getPermissionList(AuthPrincipal ignored) {
                return permissions.get();
            }
        });

        assertTrue(subject.isPermitted("order:read"));
        assertFalse(subject.isPermitted("order:delete"));
        assertTrue(subject.isPermitted("user-9", "order:read"));
        assertArrayEquals(new boolean[]{true, false},
                subject.isPermitted(new String[]{"order:read", "order:delete"}));
        assertArrayEquals(new boolean[]{true, false},
                subject.isPermitted("user-9", new String[]{"order:read", "order:delete"}));
        assertTrue(subject.isPermittedAny(new String[]{"order:delete", "user:write"}));
        assertFalse(subject.isPermittedAny(new String[]{"order:delete"}));
        assertTrue(subject.isPermittedAny("user-9", new String[]{"order:delete", "user:write"}));
        assertTrue(subject.isPermittedAll(new String[]{"order:read", "user:write"}));
        assertFalse(subject.isPermittedAll(new String[]{"order:read", "order:delete"}));
        assertTrue(subject.isPermittedAll("user-9", new String[]{"order:read", "user:write"}));
        assertEquals(0, subject.isPermitted().length);
        assertFalse(subject.isPermittedAll());
        assertFalse(subject.isPermittedAny());
    }

    /** \u9a8c\u8bc1<code>subject.hasRole("admin")</code>\u4e3a\u771f\uff0c<code>subject.hasRole("viewer")</code>\u4e3a\u5047\uff0c<code>subject.hasAllRole()</code>\u4e3a\u5047\u3002 */
    @Test
    void roleMatrixReflectsDataProvider() {
        SecuritySubject subject = subject();
        subject.login(new AuthRequest("user-10").setPrincipal(principal("user-10")));
        AtomicReference<List<String>> roles = new AtomicReference<>(Arrays.asList("admin", "editor"));
        SubjectKit.setDataProvider(new SubjectDataProvider() {
            @Override
            public List<String> getRoleList(AuthPrincipal ignored) {
                return roles.get();
            }
        });

        assertTrue(subject.hasRole("admin"));
        assertFalse(subject.hasRole("viewer"));
        assertTrue(subject.hasRole("user-10", "admin"));
        assertArrayEquals(new boolean[]{true, false},
                subject.hasRoles(new String[]{"admin", "viewer"}));
        assertArrayEquals(new boolean[]{true, false},
                subject.hasRoles("user-10", new String[]{"admin", "viewer"}));
        assertTrue(subject.hasAnyRole(new String[]{"viewer", "editor"}));
        assertFalse(subject.hasAnyRole(new String[]{"viewer"}));
        assertTrue(subject.hasAnyRole("user-10", new String[]{"viewer", "editor"}));
        assertTrue(subject.hasAllRole(new String[]{"admin", "editor"}));
        assertFalse(subject.hasAllRole(new String[]{"admin", "viewer"}));
        assertTrue(subject.hasAllRole("user-10", new String[]{"admin", "editor"}));
        assertEquals(0, subject.hasRoles().length);
        assertFalse(subject.hasAnyRole());
        assertFalse(subject.hasAllRole());
    }

    /** \u9a8c\u8bc1<code>subject.isPermitted("nobody", new String[]{"a"}).length</code> \u7b49\u4e8e <code>0</code>\uff0c<code>subject.isPermittedAny("nobody", new String[]{"a"})</code>\u4e3a\u5047\uff0c<code>subject.hasAllRole("nobody", new String[]{"admin"})</code>\u4e3a\u5047\u3002 */
    @Test
    void loginIdVariantChecksReturnDefaultsForUnknownAccount() {
        SecuritySubject subject = subject();

        assertEquals(0, subject.isPermitted("nobody", new String[]{"a"}).length);
        assertFalse(subject.isPermittedAny("nobody", new String[]{"a"}));
        assertFalse(subject.isPermittedAll("nobody", new String[]{"a"}));
        assertEquals(0, subject.hasRoles("nobody", new String[]{"admin"}).length);
        assertFalse(subject.hasAnyRole("nobody", new String[]{"admin"}));
        assertFalse(subject.hasAllRole("nobody", new String[]{"admin"}));
    }

}
