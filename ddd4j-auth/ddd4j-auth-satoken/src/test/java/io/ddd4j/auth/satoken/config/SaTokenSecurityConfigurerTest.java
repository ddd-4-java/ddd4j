package io.ddd4j.auth.satoken.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * 验证 Sa-Token 生产安全配置在修改全局状态前完成校验。
 */
class SaTokenSecurityConfigurerTest {

    private SaTokenConfig originalConfig;
    private StpLogic originalLogic;

    @BeforeEach
    void setUp() {
        originalConfig = SaManager.getConfig();
        originalLogic = StpUtil.stpLogic;
        SaManager.setConfig(new SaTokenConfig().setIsPrint(false).setIsLog(false));
    }

    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(originalLogic);
        SaManager.setConfig(originalConfig);
    }

    @Test
    void configure_shouldInstallNamespacedSessionConfiguration() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setTokenNamespace("orders");
        properties.setLoginType("order-user");
        properties.setTokenTtlSeconds(300L);
        properties.setActiveTimeoutSeconds(60L);

        StpLogic logic = SaTokenSecurityConfigurer.configure(properties);

        assertThat(logic).isNotInstanceOf(Ddd4jStpLogicJwtForSimple.class);
        assertThat(SaManager.getConfig().getTokenName()).isEqualTo("orders-token");
        assertThat(SaManager.getConfig().getTimeout()).isEqualTo(300L);
        assertThat(SaManager.getConfig().getActiveTimeout()).isEqualTo(60L);
        assertThat(SaManager.getConfig().getTokenSessionCheckLogin()).isTrue();
    }

    @Test
    void configure_shouldRejectJwtWithoutProductionSecret() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        properties.setJwtSecretKey("too-short");
        properties.setJwtIssuer("ddd4j-test");
        properties.setJwtAudience("ddd4j-tests");

        assertThatIllegalArgumentException().isThrownBy(() -> SaTokenSecurityConfigurer.configure(properties))
                .withMessageContaining("jwtSecretKey");
    }

    @Test
    void configure_shouldRejectJwtWithoutIssuerOrAudience() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        properties.setJwtSecretKey("ddd4j-satoken-subject-test-secret-32bytes");

        assertThatIllegalArgumentException().isThrownBy(() -> SaTokenSecurityConfigurer.configure(properties))
                .withMessageContaining("jwtIssuer");
    }
}
