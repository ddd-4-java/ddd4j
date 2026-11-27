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
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.mock.SaRequestForMock;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.auth.satoken.config.Ddd4jStpLogicJwtForSimple;
import io.ddd4j.auth.satoken.config.SaTokenSecurityProperties;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.auth.session.AuthSessionConfig;
import io.ddd4j.core.constant.AuthConstants;
import io.ddd4j.core.exception.AuthorizationException;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.util.SubjectKit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SaTokenDataIdentityContractTest {

    private static final String TOKEN_CONTEXT = "io.ddd4j.boot.auth.satoken.SaTokenDataSubjectProvider.context";

    private final Subject subject = new SaTokenDataSubjectProvider().getSubject();
    private SaTokenConfig originalConfig;
    private SaTokenDao originalDao;
    private StpLogic originalLogic;
    private TestStpLogic logic;

    @BeforeEach
    void setUp() {
        originalConfig = SaManager.getConfig();
        originalDao = SaManager.getSaTokenDao();
        originalLogic = StpUtil.stpLogic;
        SaManager.setConfig(new SaTokenConfig().setIsPrint(false).setIsLog(false));
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        logic = new TestStpLogic();
        StpUtil.setStpLogic(logic);
        SaTokenContextMockUtil.setMockContext();
    }

    @AfterEach
    void tearDown() {
        SaTokenContextMockUtil.clearContext();
        StpUtil.setStpLogic(originalLogic);
        SaManager.setSaTokenDao(originalDao);
        SaManager.setConfig(originalConfig);
    }

    @Test
    void isolatesTwoTokensOfTheSameAccount() {
        AuthPrincipal first = principal("tenant-one", "shop");
        AuthPrincipal second = principal("tenant-two", "admin");
        String firstToken = login(first);
        String secondToken = login(second);

        assertNotEquals(firstToken, secondToken);
        assertSame(first, subject.verify(firstToken));
        assertSame(second, subject.getPrincipalByToken(secondToken));

        openRequest(firstToken);
        assertSame(first, subject.getPrincipal());
        assertSame(first, subject.getPrincipalByLoginId("shared-account"));
        SaTokenDataIdentity firstIdentity = SaTokenDataIdentity.bindCurrentRequest();
        assertEquals("tenant-one", firstIdentity.getTenantId());
        assertEquals("shop", firstIdentity.getAppId());
        assertTrue(SaTokenDataIdentity.permits("user", 42));
        assertFalse(SaTokenDataIdentity.permits("tenant", "tenant-two"));
        assertFalse(SaTokenDataIdentity.permits("unknown", "shop"));

        openRequest(secondToken);
        assertNull(SaTokenDataIdentity.current());
        assertSame(second, subject.getPrincipal());
        assertEquals("tenant-two", SaTokenDataIdentity.bindCurrentRequest().getTenantId());
        assertTrue(SaTokenDataIdentity.permits("application", "admin"));
    }

    @Test
    void validatesRequestTargetsAgainstTokenSession() {
        login(principal("tenant-one", "shop"));
        SaTokenDataIdentity identity = SaTokenDataIdentity.bindCurrentRequest();
        Map<String, List<String>> headers = new HashMap<>();
        identity.requireMatchingTargets(name -> headers.getOrDefault(name, Collections.emptyList()));

        headers.put("X-Tenant-Id", Collections.singletonList("tenant-one"));
        headers.put("X-App-Id", Collections.singletonList("shop"));
        headers.put("X-User-Id", Collections.singletonList("42"));
        identity.requireMatchingTargets(name -> headers.getOrDefault(name, Collections.emptyList()));

        headers.put("X-Tenant-Id", Collections.singletonList("tenant-two"));
        assertThrows(AuthorizationException.class,
                () -> identity.requireMatchingTargets(name -> headers.getOrDefault(name, Collections.emptyList())));
        headers.put("X-Tenant-Id", Collections.singletonList("tenant-one"));
        headers.put("X-App-Id", Collections.singletonList("admin"));
        assertThrows(AuthorizationException.class,
                () -> identity.requireMatchingTargets(name -> headers.getOrDefault(name, Collections.emptyList())));
        headers.put("X-App-Id", Collections.singletonList("shop"));
        headers.put("X-User-Id", Arrays.asList("42", "42"));
        assertThrows(AuthorizationException.class,
                () -> identity.requireMatchingTargets(name -> headers.getOrDefault(name, Collections.emptyList())));
    }

    @Test
    void clearsBindingWhenTheRequestTokenChanges() {
        String firstToken = login(principal("tenant-one", "shop"));
        String secondToken = login(principal("tenant-two", "admin"));
        openRequest(firstToken);
        SaTokenDataIdentity.bindCurrentRequest();

        logic.setTokenValueToStorage(secondToken);
        assertNull(SaTokenDataIdentity.current());
        assertFalse(SaTokenDataIdentity.permits("tenant", "tenant-one"));
        assertEquals("tenant-two", SaTokenDataIdentity.bindCurrentRequest().getTenantId());
    }

    @Test
    void clearsBindingWhenTheTokenIsRevoked() {
        String token = login(principal("tenant-one", "shop"));
        AuthPrincipal other = principal("tenant-two", "admin");
        String otherToken = login(other);
        openRequest(token);
        SaTokenDataIdentity.bindCurrentRequest();

        logic.logoutByTokenValue(token);
        assertNull(SaTokenDataIdentity.current());
        assertNull(subject.verify(token));
        assertNull(subject.getPrincipal());
        assertNull(subject.getPrincipalByLoginId("shared-account"));
        assertFalse(subject.isAuthenticated());
        assertFalse(subject.isAuthenticated("shared-account"));
        assertFalse(SaTokenDataIdentity.permits("tenant", "tenant-one"));
        assertSame(other, subject.verify(otherToken));
    }

    @Test
    void aFailedRebindDoesNotLeaveThePreviousRequestIdentity() {
        login(principal("tenant-one", "shop"));
        SaTokenDataIdentity.bindCurrentRequest();
        logic.failTokenLookup = true;

        assertThrows(IllegalStateException.class, SaTokenDataIdentity::bindCurrentRequest);
        logic.failTokenLookup = false;

        assertNull(SaTokenDataIdentity.current());
    }

    @Test
    void verifiesAndReadsTheExistingBootTokenSessionFormat() {
        AuthPrincipal principal = principal("tenant-one", "shop");
        String token = new SaTokenSubject().login(AuthRequest.of(principal.getLoginId()).setPrincipal(principal));
        Map<String, Object> legacyContext = new HashMap<>();
        legacyContext.put("principal", principal);
        legacyContext.put("tenantId", "tenant-one");
        legacyContext.put("appId", "shop");
        legacyContext.put("userId", "42");
        logic.getTokenSessionByToken(token, true).set(TOKEN_CONTEXT, legacyContext);

        assertEquals("tenant-one", SaTokenDataIdentity.bindCurrentRequest().getTenantId());
        assertSame(principal, subject.verify(token));

        logic.getTokenSessionByToken(token, false).delete(TOKEN_CONTEXT);
        assertNull(SaTokenDataIdentity.current());
        assertNull(SaTokenDataIdentity.bindCurrentRequest());
        assertNull(subject.verify(token));
        assertSame(principal, new SaTokenSubject().verify(token));
    }

    @Test
    void missingTokenContextCannotInheritAnotherTokensPrincipal() {
        AuthPrincipal first = principal("tenant-one", "shop");
        AuthPrincipal second = principal("tenant-two", "admin");
        String firstToken = login(first);
        String secondToken = login(second);
        openRequest(firstToken);

        assertSame(first, subject.getPrincipal());
        assertSame(first, subject.getPrincipalByLoginId("shared-account"));
        logic.getTokenSessionByToken(firstToken, false).delete(TOKEN_CONTEXT);

        assertAll(
                () -> assertNull(subject.verify(firstToken)),
                () -> assertNull(subject.getPrincipalByToken(firstToken)),
                () -> assertNull(subject.getPrincipal()),
                () -> assertNull(subject.getPrincipalByLoginId("shared-account")),
                () -> assertSame(second, subject.verify(secondToken)));
    }

    @Test
    void identityFieldsWithoutATokenSessionPrincipalGrantNoAuthority() {
        String token = login(principal("tenant-one", "shop"));
        SaTokenDataIdentity.bindCurrentRequest();

        Map<String, Object> malformed = new HashMap<>();
        malformed.put("tenantId", "tenant-one");
        malformed.put("appId", "shop");
        malformed.put("userId", "42");
        logic.getTokenSessionByToken(token, false).set(TOKEN_CONTEXT, malformed);

        assertAll(
                () -> assertNull(SaTokenDataIdentity.current()),
                () -> assertNull(SaTokenDataIdentity.bindCurrentRequest()),
                () -> assertNull(subject.verify(token)),
                () -> assertNull(subject.getPrincipal()),
                () -> assertNull(subject.getPrincipalByLoginId("shared-account")));
    }

    @Test
    void explicitOtherAccountLookupSupportsNoCredentialOrAValidScopedCredential() {
        AuthPrincipal otherAccount = principal("tenant-two", "admin").setLoginId("other-account");
        login(otherAccount);
        new SaTokenSubject().login(AuthRequest.of("status-only-account"));
        String token = login(principal("tenant-one", "shop"));

        SaTokenContextMockUtil.clearContext();
        assertNull(subject.getPrincipal());
        assertFalse(subject.isAuthenticated());
        assertSame(otherAccount, subject.getPrincipalByLoginId("other-account"));
        assertTrue(subject.isAuthenticated("status-only-account"));

        openRequest(token);
        assertSame(otherAccount, subject.getPrincipalByLoginId("other-account"));
        assertTrue(subject.isAuthenticated("status-only-account"));

        openRequest("unknown-token");
        assertNull(subject.getPrincipal());
        assertNull(subject.getPrincipalByLoginId("other-account"));
        assertFalse(subject.isAuthenticated("status-only-account"));
    }

    @Test
    void malformedAuthorizationHeaderCannotBeTreatedAsNoCredential() {
        AuthPrincipal other = principal("tenant-two", "admin").setLoginId("other-account");
        login(other);
        String token = login(principal("tenant-one", "shop"));
        SaManager.getConfig().setTokenPrefix("Bearer");
        SaTokenContextMockUtil.setMockContext();
        SaRequestForMock request = (SaRequestForMock) SaHolder.getRequest();
        String header = logic.getTokenName();

        request.headerMap.put(header, "Bearer " + token);
        assertSame(other, subject.getPrincipalByLoginId("other-account"));
        assertTrue(subject.isAuthenticated("other-account"));

        request.headerMap.put(header, "Basic " + token);
        assertAll(
                () -> assertNull(subject.getPrincipalByLoginId("other-account")),
                () -> assertFalse(subject.isAuthenticated("other-account")));

        request.headerMap.put(header, "Bearer ");
        assertAll(
                () -> assertNull(subject.getPrincipalByLoginId("other-account")),
                () -> assertFalse(subject.isAuthenticated("other-account")));
    }

    @Test
    void jwtAudienceRotationRejectsCurrentPrincipalAndPermissionsEvenWithAnActiveMapping() {
        SubjectDataProvider previous = SubjectKit.dataProvider;
        SubjectKit.setDataProvider(new SubjectDataProvider() {
            @Override
            public List<String> getPermissionList(AuthPrincipal principal) {
                return Collections.singletonList("orders:read");
            }
        });
        try {
            useJwtAudience("previous-audience");
            AuthPrincipal principal = principal("tenant-one", "shop");
            String token = login(principal);
            assertSame(principal, subject.verify(token));
            assertTrue(subject.isPermitted("orders:read"));
            assertTrue(subject.isPermitted("shared-account", "orders:read"));
            assertTrue(subject.isAuthenticated());
            assertTrue(subject.isAuthenticated("shared-account"));
            SaTokenDataIdentity.bindCurrentRequest();

            StpLogic rotated = useJwtAudience("current-audience");
            rotated.setTokenValueToStorage(token);
            assertEquals("shared-account", rotated.getLoginIdByToken(token));

            assertAll(
                    () -> assertNull(subject.verify(token)),
                    () -> assertNull(subject.getPrincipal()),
                    () -> assertNull(subject.getPrincipalByLoginId("shared-account")),
                    () -> assertFalse(subject.isPermitted("orders:read")),
                    () -> assertFalse(subject.isPermitted("shared-account", "orders:read")),
                    () -> assertFalse(subject.isAuthenticated()),
                    () -> assertFalse(subject.isAuthenticated("shared-account")),
                    () -> assertNull(SaTokenDataIdentity.current()),
                    () -> assertNull(SaTokenDataIdentity.bindCurrentRequest()));
        } finally {
            SubjectKit.setDataProvider(previous);
        }
    }

    @Test
    void accountSessionCannotSupplyMissingTokenIdentity() {
        AuthPrincipal unscoped = principal(null, "shop");
        String token = login(unscoped);
        login(principal("other-tenant", "admin"));

        openRequest(token);
        assertSame(unscoped, subject.verify(token));
        assertNull(SaTokenDataIdentity.bindCurrentRequest());
        assertFalse(SaTokenDataIdentity.permits("tenant", "other-tenant"));
    }

    @Test
    void optionalProviderRejectsPresetTokensAndKeepsCallerConfiguration() {
        AuthPrincipal principal = principal("tenant-one", "shop");
        AuthSessionConfig config = new AuthSessionConfig().setPresetToken("caller-supplied");
        AuthRequest request = AuthRequest.of(principal.getLoginId()).setPrincipal(principal).setSessionConfig(config);
        assertThrows(IllegalArgumentException.class, () -> subject.login(request));
        config.setPresetToken(null).setShare(true).setTimeout(120L);

        String token = subject.login(request);

        assertTrue(config.isShare());
        assertEquals(120L, config.getTimeout());
        long timeout = logic.getTokenTimeout(token);
        assertTrue(timeout > 0 && timeout <= 120L);
        assertEquals("tenant-one", SaTokenDataIdentity.bindCurrentRequest().getTenantId());
    }

    @Test
    void auditProjectsTheValidatedIdentityAfterRequestCompletion() {
        login(principal("tenant-one", "shop"));
        SaTokenDataIdentity identity = SaTokenDataIdentity.bindCurrentRequest();
        SaTokenContextMockUtil.clearContext();
        assertNull(SaTokenDataIdentity.current());
        AtomicReference<SaTokenDataAccessAudit> observed = new AtomicReference<>();
        SaTokenDataAccessAudit.publish(identity, "GET", "/orders/{id}", 403, observed::set);

        SaTokenDataAccessAudit audit = observed.get();
        assertSame(identity, audit.getIdentity());
        assertEquals("tenant-one", audit.getIdentity().getTenantId());
        assertEquals("shop", audit.getIdentity().getAppId());
        assertEquals("42", audit.getIdentity().getUserId());
        assertEquals("GET", audit.getMethod());
        assertEquals("/orders/{id}", audit.getOperation());
        assertEquals(403, audit.getStatus());
        SaTokenDataAccessAudit.publish(identity, "GET", "/orders/{id}", 403, event -> {
            throw new IllegalStateException("listener failed");
        });
    }

    private String login(AuthPrincipal principal) {
        return subject.login(AuthRequest.of(principal.getLoginId()).setPrincipal(principal));
    }

    private AuthPrincipal principal(String tenant, String app) {
        AuthPrincipal principal = new AuthPrincipal().setLoginId("shared-account").setUserId(42).setAppId(app);
        if (Objects.nonNull(tenant)) {
            principal.getProfile().put(AuthConstants.FIELD_TENANT_ID, tenant);
        }
        return principal;
    }

    private StpLogic useJwtAudience(String audience) {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setLoginType(StpUtil.TYPE);
        properties.setJwtSecretKey("ddd4j-data-identity-test-32-byte-key");
        properties.setJwtIssuer("ddd4j-data-identity-test");
        properties.setJwtAudience(audience);
        StpLogic jwt = new Ddd4jStpLogicJwtForSimple(properties);
        StpUtil.setStpLogic(jwt);
        return jwt;
    }

    private void openRequest(String token) {
        SaTokenContextMockUtil.setMockContext();
        logic.setTokenValueToStorage(token);
    }

    private static final class TestStpLogic extends StpLogic {

        private boolean failTokenLookup;

        private TestStpLogic() {
            super(StpUtil.TYPE);
        }

        @Override
        public String getTokenValue() {
            if (failTokenLookup) {
                throw new IllegalStateException("request token lookup failed");
            }
            return super.getTokenValue();
        }
    }
}
