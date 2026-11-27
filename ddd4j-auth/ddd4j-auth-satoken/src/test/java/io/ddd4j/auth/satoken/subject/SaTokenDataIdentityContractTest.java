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

/**
 * 验证 <code>SaTokenDataIdentity</code> 在<code>clearsBinding</code>、<code>identityFields</code>、<code>jwtAudienceRotation</code>、<code>optionalProvider</code>等操作中的行为契约。
 */
class SaTokenDataIdentityContractTest {

    /**
     * 测试会话中保存认证主体上下文的属性键。
     */
    private static final String TOKEN_CONTEXT = "io.ddd4j.boot.auth.satoken.SaTokenDataSubjectProvider.context";

    /**
     * 当前用例使用的认证主体。
     */
    private final Subject subject = new SaTokenDataSubjectProvider().getSubject();
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
     * 当前用例安装或用来校验令牌的原生登录逻辑。
     */
    private TestStpLogic logic;

    /**
     * 测试前依次恢复或安装配置<code>SaManager</code>、恢复或安装原生存储<code>SaManager</code>、恢复或安装登录逻辑<code>StpUtil</code>。
     */
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

    /**
     * 测试后依次清除上下文<code>SaTokenContextMockUtil</code>、恢复或安装登录逻辑<code>StpUtil</code>、恢复或安装原生存储<code>SaManager</code>、恢复或安装配置<code>SaManager</code>。
     */
    @AfterEach
    void tearDown() {
        SaTokenContextMockUtil.clearContext();
        StpUtil.setStpLogic(originalLogic);
        SaManager.setSaTokenDao(originalDao);
        SaManager.setConfig(originalConfig);
    }

    /**
     * 验证<code>secondToken</code> 不等于 <code>firstToken</code>，<code>subject.verify(firstToken)</code> 与…为同一实例 <code>first</code>，<code>SaTokenDataIdentity.permits("application", "admin")</code>为真。
     */
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

    /**
     * 验证被测调用 抛出 <code>AuthorizationException.class</code>。
     */
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

    /**
     * 验证<code>SaTokenDataIdentity.current()</code>为空，<code>SaTokenDataIdentity.permits("tenant", "tenant-one")</code>为假，<code>SaTokenDataIdentity.bindCurrentRequest().getTenantId()</code> 等于 <code>"tenant-two"</code>。
     */
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

    /**
     * 验证<code>SaTokenDataIdentity.current()</code>为空，<code>subject.verify(token)</code>为空，<code>subject.verify(otherToken)</code> 与…为同一实例 <code>other</code>。
     */
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

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>，<code>SaTokenDataIdentity.current()</code>为空。
     */
    @Test
    void aFailedRebindDoesNotLeaveThePreviousRequestIdentity() {
        login(principal("tenant-one", "shop"));
        SaTokenDataIdentity.bindCurrentRequest();
        logic.failTokenLookup = true;

        assertThrows(IllegalStateException.class, SaTokenDataIdentity::bindCurrentRequest);
        logic.failTokenLookup = false;

        assertNull(SaTokenDataIdentity.current());
    }

    /**
     * 验证<code>SaTokenDataIdentity.bindCurrentRequest().getTenantId()</code> 等于 <code>"tenant-one"</code>，<code>subject.verify(token)</code> 与…为同一实例 <code>principal</code>，<code>subject.verify(token)</code>为空。
     */
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

    /**
     * 验证<code>subject.getPrincipal()</code> 与…为同一实例 <code>first</code>，<code>subject.getPrincipalByLoginId("shared-account")</code> 与…为同一实例 <code>first</code>，<code>subject.verify(secondToken)</code> 与…为同一实例 <code>second</code>。
     */
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

    /**
     * 验证<code>SaTokenDataIdentity.current()</code>为空，<code>SaTokenDataIdentity.bindCurrentRequest()</code>为空，<code>subject.getPrincipalByLoginId("shared-account")</code>为空。
     */
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

    /**
     * 验证<code>subject.getPrincipal()</code>为空，<code>subject.isAuthenticated()</code>为假，<code>subject.isAuthenticated("status-only-account")</code>为假。
     */
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

    /**
     * 验证<code>subject.getPrincipalByLoginId("other-account")</code> 与…为同一实例 <code>other</code>，<code>subject.isAuthenticated("other-account")</code>为真，<code>subject.isAuthenticated("other-account")</code>为假。
     */
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

    /**
     * 验证<code>subject.verify(token)</code> 与…为同一实例 <code>principal</code>，<code>subject.isPermitted("orders:read")</code>为真，<code>SaTokenDataIdentity.bindCurrentRequest()</code>为空。
     */
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

    /**
     * 验证<code>subject.verify(token)</code> 与…为同一实例 <code>unscoped</code>，<code>SaTokenDataIdentity.bindCurrentRequest()</code>为空，<code>SaTokenDataIdentity.permits("tenant", "other-tenant")</code>为假。
     */
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

    /**
     * 验证<code>subject.login(request)</code> 抛出 <code>IllegalArgumentException.class</code>，<code>config.isShare()</code>为真，<code>SaTokenDataIdentity.bindCurrentRequest().getTenantId()</code> 等于 <code>"tenant-one"</code>。
     */
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

    /**
     * 验证<code>SaTokenDataIdentity.current()</code>为空，<code>audit.getIdentity()</code> 与…为同一实例 <code>identity</code>，<code>audit.getStatus()</code> 等于 <code>403</code>。
     */
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
     * 通过 <code>subject.login()</code> 取得当前测试需要的结果。
     *
     * @param principal 传给 <code>subject.login()</code> 的 <code>AuthPrincipal</code> 输入
     * @return 当前测试使用的结果，取自 <code>subject.login(AuthRequest.of(principal.getLoginId()).setPrincipal(principal))</code>
     */
    private String login(AuthPrincipal principal) {
        return subject.login(AuthRequest.of(principal.getLoginId()).setPrincipal(principal));
    }

    /**
     * 读取 <code>principal</code> 供当前测试使用。
     *
     * @param tenant 当前请求的数据租户
     * @param app    传给 <code>new AuthPrincipal</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>principal</code>
     */
    private AuthPrincipal principal(String tenant, String app) {
        AuthPrincipal principal = new AuthPrincipal().setLoginId("shared-account").setUserId(42).setAppId(app);
        if (Objects.nonNull(tenant)) {
            principal.getProfile().put(AuthConstants.FIELD_TENANT_ID, tenant);
        }
        return principal;
    }

    /**
     * 读取 <code>jwt</code> 供当前测试使用。
     *
     * @param audience 传给 <code>properties.setJwtAudience()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>jwt</code>
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
     * 调用 <code>SaTokenContextMockUtil.setMockContext()</code>、<code>logic.setTokenValueToStorage()</code> 执行当前夹具操作。
     *
     * @param token 从测试登录获得的会话凭据
     */
    private void openRequest(String token) {
        SaTokenContextMockUtil.setMockContext();
        logic.setTokenValueToStorage(token);
    }

    /**
     * 提供可替换的原生登录逻辑，便于验证认证行为。
     */
    private static final class TestStpLogic extends StpLogic {

        /**
         * 控制令牌查找替身是否报告故障。
         */
        private boolean failTokenLookup;

        /**
         * 使用 <code>super()</code> 初始化 <code>TestStpLogic</code> 测试夹具。
         */
        private TestStpLogic() {
            super(StpUtil.TYPE);
        }

        /**
         * 通过 <code>super.getTokenValue()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>super.getTokenValue()</code>
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
