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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies that {@link SaTokenSubject} authenticates the supplied token rather than request state.
 */
class SaTokenSubjectVerifyTest {

    /** \u8fdb\u5165\u6d4b\u8bd5\u573a\u666f\u524d\u6ce8\u518c\u7684\u914d\u7f6e\u3002 */
    private SaTokenConfig originalConfig;
    /** \u8fdb\u5165\u6d4b\u8bd5\u573a\u666f\u524d\u6ce8\u518c\u7684\u4f1a\u8bdd\u5b58\u50a8\u3002 */
    private SaTokenDao originalDao;
    /** \u8fdb\u5165\u6d4b\u8bd5\u573a\u666f\u524d\u6ce8\u518c\u7684\u8ba4\u8bc1\u903b\u8f91\u3002 */
    private StpLogic originalLogic;
    /** \u8fdb\u5165\u5f53\u524d\u6267\u884c\u8303\u56f4\u524d\u4fdd\u5b58\u7684\u7ec4\u4ef6\uff0c\u9000\u51fa\u65f6\u7528\u4e8e\u6062\u590d <code>SubjectDataProvider</code>\u3002 */
    private SubjectDataProvider originalDataProvider;

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u6062\u590d\u6216\u5b89\u88c5\u914d\u7f6e<code>SaManager</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u539f\u751f\u5b58\u50a8<code>SaManager</code>\u3002 */
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

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6062\u590d\u6216\u5b89\u88c5\u767b\u5f55\u903b\u8f91<code>StpUtil</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u539f\u751f\u5b58\u50a8<code>SaManager</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u914d\u7f6e<code>SaManager</code>\u3002 */
    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(originalLogic);
        SaManager.setSaTokenDao(originalDao);
        SaManager.setConfig(originalConfig);
        SubjectKit.setDataProvider(originalDataProvider);
    }

    /** \u9a8c\u8bc1<code>verified</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>principal</code>\u3002 */
    @Test
    void verify_shouldResolveActiveSessionToken() {
        StpLogic logic = new StpLogic(StpUtil.TYPE);
        SaTokenSubject subject = install(logic);
        AuthPrincipal principal = principal("session-user");
        String token = login(logic, principal);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isSameAs(principal);
    }

    /** \u9a8c\u8bc1<code>verified</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>principal</code>\u3002 */
    @Test
    void verify_shouldResolveSignedJwtSimpleToken() {
        Ddd4jStpLogicJwtForSimple logic = installJwtSecurity();
        SaTokenSubject subject = new SaTokenSubject();
        AuthPrincipal principal = principal("jwt-user");
        String token = login(logic, principal);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isSameAs(principal);
    }

    /** \u9a8c\u8bc1<code>verified</code>\u4e3a\u7a7a\u3002 */
    @Test
    void verify_shouldRejectTamperedJwtSimpleToken() {
        Ddd4jStpLogicJwtForSimple logic = installJwtSecurity();
        SaTokenSubject subject = new SaTokenSubject();
        String token = login(logic, principal("jwt-user"));

        AuthPrincipal verified = subject.verify(token + "tampered");

        assertThat(verified).isNull();
    }

    /** \u9a8c\u8bc1<code>verified</code>\u4e3a\u7a7a\u3002 */
    @Test
    void verify_shouldRejectJwtSimpleWithoutDdd4jSecurityContract() {
        StpLogicJwtForSimple logic = new StpLogicJwtForSimple();
        SaTokenSubject subject = install(logic);
        String token = login(logic, principal("jwt-user"));

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isNull();
    }

    /** \u9a8c\u8bc1<code>verified</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>verified</code>\u4e3a\u7a7a\u3002 */
    @Test
    void verify_shouldRejectExpiredSessionToken() {
        StpLogic logic = new StpLogic(StpUtil.TYPE);
        SaTokenSubject subject = install(logic);
        String token = login(logic, principal("session-user"));
        SaManager.getSaTokenDao().updateTimeout(logic.splicingKeyTokenValue(token), SaTokenDao.NOT_VALUE_EXPIRE);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isNull();
    }

    /** \u9a8c\u8bc1<code>verified</code>\u4e3a\u7a7a\u3002 */
    @Test
    void verify_shouldRejectExpiredJwtSimpleToken() {
        Ddd4jStpLogicJwtForSimple logic = installJwtSecurity();
        SaTokenSubject subject = new SaTokenSubject();
        String token = login(logic, principal("jwt-user"));
        SaManager.getSaTokenDao().updateTimeout(logic.splicingKeyTokenValue(token), SaTokenDao.NOT_VALUE_EXPIRE);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isNull();
    }

    /** \u9a8c\u8bc1<code>verified</code>\u4e3a\u7a7a\u3002 */
    @Test
    void verify_shouldRejectKickedOutSessionToken() {
        StpLogic logic = new StpLogic(StpUtil.TYPE);
        SaTokenSubject subject = install(logic);
        String token = login(logic, principal("session-user"));
        logic.kickoutByTokenValue(token);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isNull();
    }

    /** \u9a8c\u8bc1<code>verified</code>\u4e3a\u7a7a\u3002 */
    @Test
    void verify_shouldRejectJwtWithUnexpectedAudienceEvenWhenMapped() {
        Ddd4jStpLogicJwtForSimple logic = installJwtSecurity();
        SaTokenSubject subject = new SaTokenSubject();
        AuthPrincipal principal = principal("jwt-user");
        login(logic, principal);
        String forgedToken = cn.dev33.satoken.jwt.SaJwtUtil.createToken(logic.getLoginType(), principal.getLoginId(),
                "default-device", 60L, Map.of("iss", "ddd4j-test", "aud", "unexpected"),
                "ddd4j-satoken-subject-test-secret-32bytes");
        logic.saveTokenToIdMapping(forgedToken, principal.getLoginId(), 60L);

        AuthPrincipal verified = subject.verify(forgedToken);

        assertThat(verified).isNull();
    }

    /** \u9a8c\u8bc1<code>subject.isPermitted(principal.getLoginId(), "order:read")</code>\u4e3a\u771f\uff0c<code>subject.isPermitted(principal.getLoginId(), "order:read")</code>\u4e3a\u5047\uff0c<code>subject.isPermitted(principal.getLoginId(), "order:write")</code>\u4e3a\u771f\u3002 */
    @Test
    void permissions_shouldReflectCurrentDataProviderState() {
        StpLogic logic = new StpLogic(StpUtil.TYPE);
        SaTokenSubject subject = install(logic);
        AuthPrincipal principal = principal("permission-user");
        login(logic, principal);
        AtomicReference<List<String>> permissions = new AtomicReference<>(List.of("order:read"));
        SubjectKit.setDataProvider(new SubjectDataProvider() {
            @Override
            public List<String> getPermissionList(AuthPrincipal ignored) {
                return permissions.get();
            }
        });

        assertThat(subject.isPermitted(principal.getLoginId(), "order:read")).isTrue();
        permissions.set(List.of("order:write"));

        assertThat(subject.isPermitted(principal.getLoginId(), "order:read")).isFalse();
        assertThat(subject.isPermitted(principal.getLoginId(), "order:write")).isTrue();
    }

    /**
     * \u521b\u5efa <code>new SaTokenSubject</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param logic \u4f20\u7ed9 <code>StpUtil.setStpLogic()</code> \u7684 <code>StpLogic</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new SaTokenSubject()</code>
     */
    private SaTokenSubject install(StpLogic logic) {
        StpUtil.setStpLogic(logic);
        return new SaTokenSubject();
    }

    /**
     * \u8c03\u7528 <code>properties.setAuthenticationMode()</code>\u3001<code>properties.setJwtSecretKey()</code>\u3001<code>properties.setJwtIssuer()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>(Ddd4jStpLogicJwtForSimple)SaTokenSecurityConfigurer.configure(properties)</code>
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
     * \u8bfb\u53d6 <code>token</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param logic \u4f20\u7ed9 <code>logic.createLoginSession()</code> \u7684 <code>StpLogic</code> \u8f93\u5165
     * @param principal \u4f20\u7ed9 <code>String.valueOf()</code> \u7684 <code>AuthPrincipal</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>token</code>
     */
    private String login(StpLogic logic, AuthPrincipal principal) {
        String loginId = String.valueOf(principal.getLoginId());
        String token = logic.createLoginSession(loginId, new SaLoginParameter().setTimeout(60));
        logic.getSessionByLoginId(loginId, false).set(SaTokenSubject.PRINCIPAL_KEY, principal);
        return token;
    }

    /**
     * \u521b\u5efa <code>new AuthPrincipal</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param loginId \u6d4b\u8bd5\u4f1a\u8bdd\u4f7f\u7528\u7684\u8d26\u53f7\u6807\u8bc6
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new AuthPrincipal().setLoginId(loginId).setUserId("user-" + loginId)</code>
     */
    private AuthPrincipal principal(String loginId) {
        return new AuthPrincipal().setLoginId(loginId).setUserId("user-" + loginId);
    }
}
