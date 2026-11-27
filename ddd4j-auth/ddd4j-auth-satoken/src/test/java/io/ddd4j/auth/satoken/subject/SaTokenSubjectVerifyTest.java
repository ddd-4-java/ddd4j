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
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import io.ddd4j.auth.satoken.config.Ddd4jStpLogicJwtForSimple;
import io.ddd4j.auth.satoken.config.SaTokenAuthenticationMode;
import io.ddd4j.auth.satoken.config.SaTokenSecurityConfigurer;
import io.ddd4j.auth.satoken.config.SaTokenSecurityProperties;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.util.SubjectKit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link SaTokenSubject} authenticates the supplied token rather than request state.
 */
class SaTokenSubjectVerifyTest {

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
     * 测试前依次恢复或安装配置<code>SaManager</code>、恢复或安装原生存储<code>SaManager</code>。
     */
    @BeforeEach
    void setUp() {
        originalConfig = SaManager.getConfig();
        originalDao = SaManager.getSaTokenDao();
        originalLogic = StpUtil.stpLogic;
        originalDataProvider = SubjectKit.dataProvider;
        SaManager.setConfig(new SaTokenConfig()
                .setJwtSecretKey("ddd4j-satoken-subject-test-secret")
                .setIsPrint(false)
                .setIsLog(false));
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
    }

    /**
     * 测试后依次恢复或安装登录逻辑<code>StpUtil</code>、恢复或安装原生存储<code>SaManager</code>、恢复或安装配置<code>SaManager</code>。
     */
    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(originalLogic);
        SaManager.setSaTokenDao(originalDao);
        SaManager.setConfig(originalConfig);
        SubjectKit.setDataProvider(originalDataProvider);
    }

    /**
     * 验证<code>verified</code> 指向同一实例 <code>principal</code>。
     */
    @Test
    void verify_shouldResolveActiveSessionToken() {
        StpLogic logic = new StpLogic(StpUtil.TYPE);
        SaTokenSubject subject = install(logic);
        AuthPrincipal principal = principal("session-user");
        String token = login(logic, principal);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isSameAs(principal);
    }

    /**
     * 验证<code>verified</code> 指向同一实例 <code>principal</code>。
     */
    @Test
    void verify_shouldResolveSignedJwtSimpleToken() {
        Ddd4jStpLogicJwtForSimple logic = installJwtSecurity();
        SaTokenSubject subject = new SaTokenSubject();
        AuthPrincipal principal = principal("jwt-user");
        String token = login(logic, principal);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isSameAs(principal);
    }

    /**
     * 验证<code>verified</code>为空。
     */
    @Test
    void verify_shouldRejectTamperedJwtSimpleToken() {
        Ddd4jStpLogicJwtForSimple logic = installJwtSecurity();
        SaTokenSubject subject = new SaTokenSubject();
        String token = login(logic, principal("jwt-user"));

        AuthPrincipal verified = subject.verify(token + "tampered");

        assertThat(verified).isNull();
    }

    /**
     * 验证<code>verified</code>为空。
     */
    @Test
    void verify_shouldRejectJwtSimpleWithoutDdd4jSecurityContract() {
        StpLogicJwtForSimple logic = new StpLogicJwtForSimple();
        SaTokenSubject subject = install(logic);
        String token = login(logic, principal("jwt-user"));

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isNull();
    }

    /**
     * 验证<code>verified</code>为空。
     */
    @Test
    void verify_shouldRejectRevokedJwtSimpleToken() {
        Ddd4jStpLogicJwtForSimple logic = installJwtSecurity();
        SaTokenSubject subject = new SaTokenSubject();
        AuthPrincipal principal = principal("jwt-user");
        String token = login(logic, principal);
        logic.logoutByTokenValue(token);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isNull();
    }

    /**
     * 验证<code>verified</code>为空。
     */
    @Test
    void verify_shouldRejectExpiredSessionToken() {
        StpLogic logic = new StpLogic(StpUtil.TYPE);
        SaTokenSubject subject = install(logic);
        String token = login(logic, principal("session-user"));
        SaManager.getSaTokenDao().updateTimeout(logic.splicingKeyTokenValue(token), SaTokenDao.NOT_VALUE_EXPIRE);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isNull();
    }

    /**
     * 验证<code>verified</code>为空。
     */
    @Test
    void verify_shouldRejectExpiredJwtSimpleToken() {
        Ddd4jStpLogicJwtForSimple logic = installJwtSecurity();
        SaTokenSubject subject = new SaTokenSubject();
        String token = login(logic, principal("jwt-user"));
        SaManager.getSaTokenDao().updateTimeout(logic.splicingKeyTokenValue(token), SaTokenDao.NOT_VALUE_EXPIRE);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isNull();
    }

    /**
     * 验证<code>verified</code>为空。
     */
    @Test
    void verify_shouldRejectKickedOutSessionToken() {
        StpLogic logic = new StpLogic(StpUtil.TYPE);
        SaTokenSubject subject = install(logic);
        String token = login(logic, principal("session-user"));
        logic.kickoutByTokenValue(token);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isNull();
    }

    /**
     * 验证<code>verified</code>为空。
     */
    @Test
    void verify_shouldRejectJwtWithUnexpectedAudienceEvenWhenMapped() {
        Ddd4jStpLogicJwtForSimple logic = installJwtSecurity();
        SaTokenSubject subject = new SaTokenSubject();
        AuthPrincipal principal = principal("jwt-user");
        login(logic, principal);
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "ddd4j-test");
        claims.put("aud", "unexpected");
        String forgedToken = cn.dev33.satoken.jwt.SaJwtUtil.createToken(logic.getLoginType(), principal.getLoginId(),
                "default-device", 60L, claims,
                "ddd4j-satoken-subject-test-secret-32bytes");
        logic.saveTokenToIdMapping(forgedToken, principal.getLoginId(), 60L);

        AuthPrincipal verified = subject.verify(forgedToken);

        assertThat(verified).isNull();
    }

    /**
     * 验证<code>subject.isPermitted(principal.getLoginId(), "order:read")</code>为真，<code>subject.isPermitted(principal.getLoginId(), "order:read")</code>为假，<code>subject.isPermitted(principal.getLoginId(), "order:write")</code>为真。
     */
    @Test
    void permissions_shouldReflectCurrentDataProviderState() {
        StpLogic logic = new StpLogic(StpUtil.TYPE);
        SaTokenSubject subject = install(logic);
        AuthPrincipal principal = principal("permission-user");
        login(logic, principal);
        AtomicReference<List<String>> permissions = new AtomicReference<>(Collections.singletonList("order:read"));
        SubjectKit.setDataProvider(new SubjectDataProvider() {
            @Override
            public List<String> getPermissionList(AuthPrincipal ignored) {
                return permissions.get();
            }
        });

        assertThat(subject.isPermitted(principal.getLoginId(), "order:read")).isTrue();
        permissions.set(Collections.singletonList("order:write"));

        assertThat(subject.isPermitted(principal.getLoginId(), "order:read")).isFalse();
        assertThat(subject.isPermitted(principal.getLoginId(), "order:write")).isTrue();
    }

    /**
     * 创建 <code>new SaTokenSubject</code> 供当前测试路径调用。
     *
     * @param logic 传给 <code>StpUtil.setStpLogic()</code> 的 <code>StpLogic</code> 输入
     * @return 当前测试使用的结果，取自 <code>new SaTokenSubject()</code>
     */
    private SaTokenSubject install(StpLogic logic) {
        StpUtil.setStpLogic(logic);
        return new SaTokenSubject();
    }

    /**
     * 调用 <code>properties.setAuthenticationMode()</code>、<code>properties.setJwtSecretKey()</code>、<code>properties.setJwtIssuer()</code> 执行当前夹具操作。
     *
     * @return 当前测试使用的结果，取自 <code>(Ddd4jStpLogicJwtForSimple)SaTokenSecurityConfigurer.configure(properties)</code>
     */
    private Ddd4jStpLogicJwtForSimple installJwtSecurity() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        properties.setJwtSecretKey("ddd4j-satoken-subject-test-secret-32bytes");
        properties.setJwtIssuer("ddd4j-test");
        properties.setJwtAudience("ddd4j-tests");
        return (Ddd4jStpLogicJwtForSimple) SaTokenSecurityConfigurer.configure(properties);
    }

    /**
     * 读取 <code>token</code> 供当前测试使用。
     *
     * @param logic     传给 <code>logic.createLoginSession()</code> 的 <code>StpLogic</code> 输入
     * @param principal 传给 <code>String.valueOf()</code> 的 <code>AuthPrincipal</code> 输入
     * @return 当前测试使用的结果，取自 <code>token</code>
     */
    private String login(StpLogic logic, AuthPrincipal principal) {
        String loginId = String.valueOf(principal.getLoginId());
        String token = logic.createLoginSession(loginId, new SaLoginParameter().setTimeout(60));
        logic.getSessionByLoginId(loginId, false).set(SaTokenSubject.PRINCIPAL_KEY, principal);
        return token;
    }

    /**
     * 创建 <code>new AuthPrincipal</code> 供当前测试路径调用。
     *
     * @param loginId 测试会话使用的账号标识
     * @return 当前测试使用的结果，取自 <code>new AuthPrincipal().setLoginId(loginId).setUserId("user-" + loginId)</code>
     */
    private AuthPrincipal principal(String loginId) {
        return new AuthPrincipal().setLoginId(loginId).setUserId("user-" + loginId);
    }
}
