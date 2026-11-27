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

    /**
     * 进入测试场景前注册的配置。
     */
    private SaTokenConfig originalConfig;
    /**
     * 进入测试场景前注册的认证逻辑。
     */
    private StpLogic originalLogic;

    /**
     * 测试前依次恢复或安装配置<code>SaManager</code>。
     */
    @BeforeEach
    void setUp() {
        originalConfig = SaManager.getConfig();
        originalLogic = StpUtil.stpLogic;
        SaManager.setConfig(new SaTokenConfig().setIsPrint(false).setIsLog(false));
    }

    /**
     * 测试后依次恢复或安装登录逻辑<code>StpUtil</code>、恢复或安装配置<code>SaManager</code>。
     */
    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(originalLogic);
        SaManager.setConfig(originalConfig);
    }

    /**
     * 验证<code>SaManager.getConfig().getTokenName()</code> 等于 <code>"orders-token"</code>，<code>SaManager.getConfig().getTimeout()</code> 等于 <code>300L</code>，<code>SaManager.getConfig().getTokenSessionCheckLogin()</code>为真。
     */
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

    /**
     * 验证<code>((Ddd4jStpLogicJwtForSimple) logic).jwtSecretKey()</code> 等于 <code>secret</code>。
     */
    @Test
    void configureBindsValidatedSecretToSigningLogic() {
        String secret = "a-dedicated-test-signing-secret-of-32-bytes";
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        properties.setJwtSecretKey(secret);
        properties.setJwtIssuer("orders");
        properties.setJwtAudience("clients");

        StpLogic logic = SaTokenSecurityConfigurer.configure(properties);

        assertThat(((Ddd4jStpLogicJwtForSimple) logic).jwtSecretKey()).isEqualTo(secret);
    }

    /**
     * 验证<code>SaTokenSecurityConfigurer.configure(properties)</code> 抛出 <code>IllegalArgumentException</code>。
     */
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

    /**
     * 验证<code>SaTokenSecurityConfigurer.configure(properties)</code> 抛出 <code>IllegalArgumentException</code>。
     */
    @Test
    void configure_shouldRejectJwtWithoutIssuerOrAudience() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        properties.setJwtSecretKey("ddd4j-satoken-subject-test-secret-32bytes");

        assertThatIllegalArgumentException().isThrownBy(() -> SaTokenSecurityConfigurer.configure(properties))
                .withMessageContaining("jwtIssuer");
    }
}
