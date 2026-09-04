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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecuritySubjectTest {

    private SubjectDataProvider originalDataProvider;

    private SecuritySubject subject() {
        return new SecuritySubject();
    }

    private AuthPrincipal principal(String loginId) {
        return new AuthPrincipal().setLoginId(loginId).setUserId("user-" + loginId)
                .setOrgId("org-1").setRoleId("role-1");
    }

    @BeforeEach
    void setUp() {
        originalDataProvider = SubjectKit.dataProvider;
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        SubjectKit.setDataProvider(originalDataProvider);
    }

    private void authenticate(org.springframework.security.core.Authentication auth) {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(auth);
    }

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

    @Test
    void anonymousTokenIsNotAuthenticated() {
        authenticate(new AnonymousAuthenticationToken("key", "guest",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_GUEST"));
        SecuritySubject subject = subject();

        assertNull(subject.getPrincipal());
        assertFalse(subject.isAuthenticated());
        assertNull(subject.getLoginId());
        assertFalse(subject.isPermitted("anything"));
    }

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

    @Test
    void principalFromAuthUserDetailsDelegate() {
        AuthPrincipal principal = principal("user-3");
        AuthUserDetails details = new AuthUserDetails("user-3", "pw", true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_admin")), principal;
        authenticate(new UsernamePasswordAuthenticationToken(details, "pw",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_admin"));

        assertSame(principal, subject().getPrincipal());
    }

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

    @Test
    void rememberMeTokenIsRemembered() {
        authenticate(new RememberMeAuthenticationToken("key", principal("user-5"),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_admin"));

        SecuritySubject subject = subject();
        assertTrue(subject.isAuthenticated());
        assertTrue(subject.isRemembered());
        assertEquals("user-5", subject.getPrincipal().getLoginId());
    }

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

    @Test
    void refreshReturnsCurrentName() {
        SecuritySubject subject = subject();
        subject.login(new AuthRequest("user-7"));

        assertEquals("user-7", subject.refresh());
    }

    @Test
    void getExtraReadsPrincipalProfile() {
        SecuritySubject subject = subject();
        AuthPrincipal principal = principal("user-8");
        principal.getProfile().put("channel", "web");
        subject.login(new AuthRequest("user-8").setPrincipal(principal));

        assertEquals("web", subject.getExtra("ignored-token", "channel"));
        assertNull(subject.getExtra("ignored-token", "missing"));
    }

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
