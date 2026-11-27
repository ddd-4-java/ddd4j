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

    /** \u8fdb\u5165\u6d4b\u8bd5\u573a\u666f\u524d\u6ce8\u518c\u7684\u914d\u7f6e\u3002 */
    private SaTokenConfig originalConfig;
    /** \u8fdb\u5165\u6d4b\u8bd5\u573a\u666f\u524d\u6ce8\u518c\u7684\u8ba4\u8bc1\u903b\u8f91\u3002 */
    private StpLogic originalLogic;

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u6062\u590d\u6216\u5b89\u88c5\u914d\u7f6e<code>SaManager</code>\u3002 */
    @BeforeEach
    void setUp() {
        originalConfig = SaManager.getConfig();
        originalLogic = StpUtil.stpLogic;
        SaManager.setConfig(new SaTokenConfig().setIsPrint(false).setIsLog(false));
    }

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6062\u590d\u6216\u5b89\u88c5\u767b\u5f55\u903b\u8f91<code>StpUtil</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u914d\u7f6e<code>SaManager</code>\u3002 */
    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(originalLogic);
        SaManager.setConfig(originalConfig);
    }

    /** \u9a8c\u8bc1<code>SaManager.getConfig().getTokenName()</code> \u7b49\u4e8e <code>"orders-token"</code>\uff0c<code>SaManager.getConfig().getTimeout()</code> \u7b49\u4e8e <code>300L</code>\uff0c<code>SaManager.getConfig().getTokenSessionCheckLogin()</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>SaTokenSecurityConfigurer.configure(properties)</code> \u629b\u51fa <code>IllegalArgumentException</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>SaTokenSecurityConfigurer.configure(properties)</code> \u629b\u51fa <code>IllegalArgumentException</code>\u3002 */
    @Test
    void configure_shouldRejectJwtWithoutIssuerOrAudience() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        properties.setJwtSecretKey("ddd4j-satoken-subject-test-secret-32bytes");

        assertThatIllegalArgumentException().isThrownBy(() -> SaTokenSecurityConfigurer.configure(properties))
                .withMessageContaining("jwtIssuer");
    }
}
