package io.ddd4j.auth.satoken.subject;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import io.ddd4j.core.auth.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link SaTokenSubject} authenticates the supplied token rather than request state.
 */
class SaTokenSubjectVerifyTest {

    private SaTokenConfig originalConfig;
    private SaTokenDao originalDao;
    private StpLogic originalLogic;

    @BeforeEach
    void setUp() {
        originalConfig = SaManager.getConfig();
        originalDao = SaManager.getSaTokenDao();
        originalLogic = StpUtil.stpLogic;
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
        StpLogicJwtForSimple logic = new StpLogicJwtForSimple();
        SaTokenSubject subject = install(logic);
        AuthPrincipal principal = principal("jwt-user");
        String token = login(logic, principal);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isSameAs(principal);
    }

    @Test
    void verify_shouldRejectTamperedJwtSimpleToken() {
        StpLogicJwtForSimple logic = new StpLogicJwtForSimple();
        SaTokenSubject subject = install(logic);
        String token = login(logic, principal("jwt-user"));

        AuthPrincipal verified = subject.verify(token + "tampered");

        assertThat(verified).isNull();
    }

    @Test
    void verify_shouldRejectRevokedJwtSimpleToken() {
        StpLogicJwtForSimple logic = new StpLogicJwtForSimple();
        SaTokenSubject subject = install(logic);
        AuthPrincipal principal = principal("jwt-user");
        String token = login(logic, principal);
        logic.logoutByTokenValue(token);

        AuthPrincipal verified = subject.verify(token);

        assertThat(verified).isNull();
    }

    private SaTokenSubject install(StpLogic logic) {
        StpUtil.setStpLogic(logic);
        return new SaTokenSubject();
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
