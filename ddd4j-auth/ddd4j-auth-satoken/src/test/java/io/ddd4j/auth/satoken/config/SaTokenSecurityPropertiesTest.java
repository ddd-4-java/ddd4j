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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 <code>SaTokenSecurityProperties</code> 在<code>validate</code>等操作中的行为契约。
 */
class SaTokenSecurityPropertiesTest {

    /**
     * 验证缺省认证安全配置可直接通过校验。
     */
    @Test
    void defaultsAreValid() {
        assertDoesNotThrow(new SaTokenSecurityProperties()::validate);
    }

    /**
     * 验证被测调用 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void validateRejectsInvalidTokenNamespace() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setTokenNamespace("1invalid");
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    /**
     * 验证被测调用 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void validateRejectsInvalidLoginType() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setLoginType("");
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    /**
     * 验证被测调用 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void validateRejectsNonPositiveTokenTtl() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setTokenTtlSeconds(0L);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    /**
     * 验证被测调用 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void validateRejectsInvalidActiveTimeout() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setActiveTimeoutSeconds(0L);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    /**
     * 验证被测调用 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void validateRejectsNullAuthenticationMode() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(null);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    /**
     * 验证被测调用 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void validateJwtModeRequiresAudienceAndIssuer() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        properties.setJwtSecretKey("ddd4j-props-test-secret-32bytes");
        assertThrows(IllegalArgumentException.class, properties::validate);

        SaTokenSecurityProperties missingIssuer = new SaTokenSecurityProperties();
        missingIssuer.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        missingIssuer.setJwtSecretKey("ddd4j-props-test-secret-32bytes");
        missingIssuer.setJwtAudience("aud");
        assertThrows(IllegalArgumentException.class, missingIssuer::validate);
    }

    /**
     * 在测试框架中执行 <code>properties.setAuthenticationMode()</code>、<code>properties.setJwtSecretKey()</code>、<code>properties.setJwtIssuer()</code>，验证该场景。
     */
    @Test
    void validateJwtModeAcceptsCompleteConfiguration() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        properties.setJwtSecretKey("ddd4j-props-test-secret-32bytes-longer");
        properties.setJwtIssuer("issuer");
        properties.setJwtAudience("audience");
        assertDoesNotThrow(properties::validate);
    }

    /**
     * 验证默认命名空间与认证域拼接成 ddd4j-token。
     */
    @Test
    void tokenNameCombinesNamespaceAndLoginType() {
        assertEquals("ddd4j-token", new SaTokenSecurityProperties().tokenName());
    }
}
