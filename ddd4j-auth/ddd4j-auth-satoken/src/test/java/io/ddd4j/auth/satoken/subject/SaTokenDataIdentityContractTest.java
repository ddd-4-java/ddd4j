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

/** \u9a8c\u8bc1 <code>SaTokenDataIdentity</code> \u5728<code>clearsBinding</code>\u3001<code>identityFields</code>\u3001<code>jwtAudienceRotation</code>\u3001<code>optionalProvider</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class SaTokenDataIdentityContractTest {

    /** \u6d4b\u8bd5\u4f1a\u8bdd\u4e2d\u4fdd\u5b58\u8ba4\u8bc1\u4e3b\u4f53\u4e0a\u4e0b\u6587\u7684\u5c5e\u6027\u952e\u3002 */
    private static final String TOKEN_CONTEXT = "io.ddd4j.boot.auth.satoken.SaTokenDataSubjectProvider.context";

    /** \u5f53\u524d\u7528\u4f8b\u4f7f\u7528\u7684\u8ba4\u8bc1\u4e3b\u4f53\u3002 */
    private final Subject subject = new SaTokenDataSubjectProvider().getSubject();
    /** \u8fdb\u5165\u6d4b\u8bd5\u573a\u666f\u524d\u6ce8\u518c\u7684\u914d\u7f6e\u3002 */
    private SaTokenConfig originalConfig;
    /** \u8fdb\u5165\u6d4b\u8bd5\u573a\u666f\u524d\u6ce8\u518c\u7684\u4f1a\u8bdd\u5b58\u50a8\u3002 */
    private SaTokenDao originalDao;
    /** \u8fdb\u5165\u6d4b\u8bd5\u573a\u666f\u524d\u6ce8\u518c\u7684\u8ba4\u8bc1\u903b\u8f91\u3002 */
    private StpLogic originalLogic;
    /** \u5f53\u524d\u7528\u4f8b\u5b89\u88c5\u6216\u7528\u6765\u6821\u9a8c\u4ee4\u724c\u7684\u539f\u751f\u767b\u5f55\u903b\u8f91\u3002 */
    private TestStpLogic logic;

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u6062\u590d\u6216\u5b89\u88c5\u914d\u7f6e<code>SaManager</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u539f\u751f\u5b58\u50a8<code>SaManager</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u767b\u5f55\u903b\u8f91<code>StpUtil</code>\u3002 */
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

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6e05\u9664\u4e0a\u4e0b\u6587<code>SaTokenContextMockUtil</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u767b\u5f55\u903b\u8f91<code>StpUtil</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u539f\u751f\u5b58\u50a8<code>SaManager</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u914d\u7f6e<code>SaManager</code>\u3002 */
    @AfterEach
    void tearDown() {
        SaTokenContextMockUtil.clearContext();
        StpUtil.setStpLogic(originalLogic);
        SaManager.setSaTokenDao(originalDao);
        SaManager.setConfig(originalConfig);
    }

    /** \u9a8c\u8bc1<code>secondToken</code> \u4e0d\u7b49\u4e8e <code>firstToken</code>\uff0c<code>subject.verify(firstToken)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>first</code>\uff0c<code>SaTokenDataIdentity.permits("application", "admin")</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>AuthorizationException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>SaTokenDataIdentity.current()</code>\u4e3a\u7a7a\uff0c<code>SaTokenDataIdentity.permits("tenant", "tenant-one")</code>\u4e3a\u5047\uff0c<code>SaTokenDataIdentity.bindCurrentRequest().getTenantId()</code> \u7b49\u4e8e <code>"tenant-two"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>SaTokenDataIdentity.current()</code>\u4e3a\u7a7a\uff0c<code>subject.verify(token)</code>\u4e3a\u7a7a\uff0c<code>subject.verify(otherToken)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>other</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>SaTokenDataIdentity.current()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void aFailedRebindDoesNotLeaveThePreviousRequestIdentity() {
        login(principal("tenant-one", "shop"));
        SaTokenDataIdentity.bindCurrentRequest();
        logic.failTokenLookup = true;

        assertThrows(IllegalStateException.class, SaTokenDataIdentity::bindCurrentRequest);
        logic.failTokenLookup = false;

        assertNull(SaTokenDataIdentity.current());
    }

    /** \u9a8c\u8bc1<code>SaTokenDataIdentity.bindCurrentRequest().getTenantId()</code> \u7b49\u4e8e <code>"tenant-one"</code>\uff0c<code>subject.verify(token)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>principal</code>\uff0c<code>subject.verify(token)</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>subject.getPrincipal()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>first</code>\uff0c<code>subject.getPrincipalByLoginId("shared-account")</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>first</code>\uff0c<code>subject.verify(secondToken)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>second</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>SaTokenDataIdentity.current()</code>\u4e3a\u7a7a\uff0c<code>SaTokenDataIdentity.bindCurrentRequest()</code>\u4e3a\u7a7a\uff0c<code>subject.getPrincipalByLoginId("shared-account")</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>subject.getPrincipal()</code>\u4e3a\u7a7a\uff0c<code>subject.isAuthenticated()</code>\u4e3a\u5047\uff0c<code>subject.isAuthenticated("status-only-account")</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>subject.getPrincipalByLoginId("other-account")</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>other</code>\uff0c<code>subject.isAuthenticated("other-account")</code>\u4e3a\u771f\uff0c<code>subject.isAuthenticated("other-account")</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>subject.verify(token)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>principal</code>\uff0c<code>subject.isPermitted("orders:read")</code>\u4e3a\u771f\uff0c<code>SaTokenDataIdentity.bindCurrentRequest()</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>subject.verify(token)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>unscoped</code>\uff0c<code>SaTokenDataIdentity.bindCurrentRequest()</code>\u4e3a\u7a7a\uff0c<code>SaTokenDataIdentity.permits("tenant", "other-tenant")</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>subject.login(request)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\uff0c<code>config.isShare()</code>\u4e3a\u771f\uff0c<code>SaTokenDataIdentity.bindCurrentRequest().getTenantId()</code> \u7b49\u4e8e <code>"tenant-one"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>SaTokenDataIdentity.current()</code>\u4e3a\u7a7a\uff0c<code>audit.getIdentity()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>identity</code>\uff0c<code>audit.getStatus()</code> \u7b49\u4e8e <code>403</code>\u3002 */
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

    /**
     * \u901a\u8fc7 <code>subject.login()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param principal \u4f20\u7ed9 <code>subject.login()</code> \u7684 <code>AuthPrincipal</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>subject.login(AuthRequest.of(principal.getLoginId()).setPrincipal(principal))</code>
     */
    private String login(AuthPrincipal principal) {
        return subject.login(AuthRequest.of(principal.getLoginId()).setPrincipal(principal));
    }

    /**
     * \u8bfb\u53d6 <code>principal</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param tenant \u5f53\u524d\u8bf7\u6c42\u7684\u6570\u636e\u79df\u6237
     * @param app \u4f20\u7ed9 <code>new AuthPrincipal</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>principal</code>
     */
    private AuthPrincipal principal(String tenant, String app) {
        AuthPrincipal principal = new AuthPrincipal().setLoginId("shared-account").setUserId(42).setAppId(app);
        if (Objects.nonNull(tenant)) {
            principal.getProfile().put(AuthConstants.FIELD_TENANT_ID, tenant);
        }
        return principal;
    }

    /**
     * \u8bfb\u53d6 <code>jwt</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param audience \u4f20\u7ed9 <code>properties.setJwtAudience()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>jwt</code>
     */
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

    /**
     * \u8c03\u7528 <code>SaTokenContextMockUtil.setMockContext()</code>\u3001<code>logic.setTokenValueToStorage()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     */
    private void openRequest(String token) {
        SaTokenContextMockUtil.setMockContext();
        logic.setTokenValueToStorage(token);
    }

    /** \u63d0\u4f9b\u53ef\u66ff\u6362\u7684\u539f\u751f\u767b\u5f55\u903b\u8f91\uff0c\u4fbf\u4e8e\u9a8c\u8bc1\u8ba4\u8bc1\u884c\u4e3a\u3002 */
    private static final class TestStpLogic extends StpLogic {

        /** \u63a7\u5236\u4ee4\u724c\u67e5\u627e\u66ff\u8eab\u662f\u5426\u62a5\u544a\u6545\u969c\u3002 */
        private boolean failTokenLookup;

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestStpLogic</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        private TestStpLogic() {
            super(StpUtil.TYPE);
        }

        /**
         * \u901a\u8fc7 <code>super.getTokenValue()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>super.getTokenValue()</code>
         */
        @Override
        public String getTokenValue() {
            if (failTokenLookup) {
                throw new IllegalStateException("request token lookup failed");
            }
            return super.getTokenValue();
        }
    }
}
