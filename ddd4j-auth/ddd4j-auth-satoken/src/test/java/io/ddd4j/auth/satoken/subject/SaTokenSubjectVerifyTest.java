package io.ddd4j.auth.satoken.subject;

import java.util.Collections;
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

    private SaTokenConfig originalConfig;
    private SaTokenDao originalDao;
    private StpLogic originalLogic;
    private SubjectDataProvider originalDataProvider;

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

    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(originalLogic);
        SaManager.setSaTokenDao(originalDao);
        SaManager.setConfig(originalConfig);
        SubjectKit.setDataProvider(originalDataProvider);
    }

    @Test
    void verify_shouldResolveActiveSessionToken() {
        StpLogic logic = new StpLogic(StpUtil.TYPE);
        SaTokenSubject subject = install(logic);
        AuthPrincipal principal = principal("session-user");
        String token = login(logic, principal);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isSameAs(principal);
    }

    @Test
    void verify_shouldResolveSignedJwtSimpleToken() {
        Ddd4jStpLogicJwtForSimple logic = installJwtSecurity();
        SaTokenSubject subject = new SaTokenSubject();
        AuthPrincipal principal = principal("jwt-user");
        String token = login(logic, principal);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isSameAs(principal);
    }

    @Test
    void verify_shouldRejectTamperedJwtSimpleToken() {
        Ddd4jStpLogicJwtForSimple logic = installJwtSecurity();
        SaTokenSubject subject = new SaTokenSubject();
        String token = login(logic, principal("jwt-user"));

        AuthPrincipal verified = subject.verify(token + "tampered");

        assertThat(verified).isNull();
    }

    @Test
    void verify_shouldRejectJwtSimpleWithoutDdd4jSecurityContract() {
        StpLogicJwtForSimple logic = new StpLogicJwtForSimple();
        SaTokenSubject subject = install(logic);
        String token = login(logic, principal("jwt-user"));

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isNull();
    }

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

    @Test
    void verify_shouldRejectExpiredSessionToken() {
        StpLogic logic = new StpLogic(StpUtil.TYPE);
        SaTokenSubject subject = install(logic);
        String token = login(logic, principal("session-user"));
        SaManager.getSaTokenDao().updateTimeout(logic.splicingKeyTokenValue(token), SaTokenDao.NOT_VALUE_EXPIRE);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isNull();
    }

    @Test
    void verify_shouldRejectExpiredJwtSimpleToken() {
        Ddd4jStpLogicJwtForSimple logic = installJwtSecurity();
        SaTokenSubject subject = new SaTokenSubject();
        String token = login(logic, principal("jwt-user"));
        SaManager.getSaTokenDao().updateTimeout(logic.splicingKeyTokenValue(token), SaTokenDao.NOT_VALUE_EXPIRE);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isNull();
    }

    @Test
    void verify_shouldRejectKickedOutSessionToken() {
        StpLogic logic = new StpLogic(StpUtil.TYPE);
        SaTokenSubject subject = install(logic);
        String token = login(logic, principal("session-user"));
        logic.kickoutByTokenValue(token);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isNull();
    }

    @Test
    void verify_shouldRejectJwtWithUnexpectedAudienceEvenWhenMapped() {
        Ddd4jStpLogicJwtForSimple logic = installJwtSecurity();
        SaTokenSubject subject = new SaTokenSubject();
        AuthPrincipal principal = principal("jwt-user");
        login(logic, principal);
        String forgedToken = cn.dev33.satoken.jwt.SaJwtUtil.createToken(logic.getLoginType(), principal.getLoginId(),
                "default-device", 60L, new java.util.LinkedHashMap<String, Object>() {{ put("iss", "ddd4j-test"); put("aud", "unexpected"); }},
                "ddd4j-satoken-subject-test-secret-32bytes");
        logic.saveTokenToIdMapping(forgedToken, principal.getLoginId(), 60L);

        AuthPrincipal verified = subject.verify(forgedToken);

        assertThat(verified).isNull();
    }

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

    private SaTokenSubject install(StpLogic logic) {
        StpUtil.setStpLogic(logic);
        return new SaTokenSubject();
    }

    private Ddd4jStpLogicJwtForSimple installJwtSecurity() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        properties.setJwtSecretKey("ddd4j-satoken-subject-test-secret-32bytes");
        properties.setJwtIssuer("ddd4j-test");
        properties.setJwtAudience("ddd4j-tests");
        return (Ddd4jStpLogicJwtForSimple) SaTokenSecurityConfigurer.configure(properties);
    }

    private String login(StpLogic logic, AuthPrincipal principal) {
        String loginId = String.valueOf(principal.getLoginId());
        String token = logic.createLoginSession(loginId, new SaLoginParameter().setTimeout(60));
        logic.getSessionByLoginId(loginId, false).set(SaTokenSubject.PRINCIPAL_KEY, principal);
        return token;
    }

    private AuthPrincipal principal(String loginId) {
        return new AuthPrincipal().setLoginId(loginId).setUserId("user-" + loginId);
    }
}
