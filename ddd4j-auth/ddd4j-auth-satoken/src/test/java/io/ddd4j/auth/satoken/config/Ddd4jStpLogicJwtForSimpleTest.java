/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.auth.satoken.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.jwt.SaJwtUtil;
import cn.dev33.satoken.jwt.exception.SaJwtException;
import cn.dev33.satoken.stp.StpLogic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 <code>Ddd4jStpLogicJwtForSimple</code> 在<code>eachInstanceIssuesAndValidates</code>等操作中的行为契约。
 */
class Ddd4jStpLogicJwtForSimpleTest {

    /**
     * 测试登录和校验使用的账号域。
     */
    private static final String LOGIN_TYPE = "jwt-signing-isolation";
    /**
     * 测试令牌声明使用的签发方。
     */
    private static final String ISSUER = "test-issuer";
    /**
     * 测试令牌声明使用的接收方。
     */
    private static final String AUDIENCE = "test-audience";
    /**
     * 构造本组测试输入时复用的固定键。
     */
    private static final String FIRST_KEY = "test-only-jwt-signing-isolation-first";
    /**
     * 构造本组测试输入时复用的固定键。
     */
    private static final String SECOND_KEY = "test-only-jwt-signing-isolation-second";
    /**
     * 构造本组测试输入时复用的固定键。
     */
    private static final String GLOBAL_KEY = "test-only-jwt-signing-isolation-global";

    /**
     * 组件装配前保存的配置，供退出时恢复。
     */
    private SaTokenConfig previousConfig;
    /**
     * 被测注册安装前的原生登录逻辑。
     */
    private StpLogic previousLogic;

    /**
     * 读取 <code>properties</code> 供当前测试使用。
     *
     * @param key 用于在测试上下文或缓存中索引数据的键
     * @return 当前测试使用的结果，取自 <code>properties</code>
     */
    private static SaTokenSecurityProperties properties(String key) {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        properties.setLoginType(LOGIN_TYPE);
        properties.setJwtSecretKey(key);
        properties.setJwtIssuer(ISSUER);
        properties.setJwtAudience(AUDIENCE);
        return properties;
    }

    /**
     * 读取 <code>claims</code> 供当前测试使用。
     *
     * @param issuer   传给 <code>claims.put()</code> 的 <code>String</code> 输入
     * @param audience 传给 <code>claims.put()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>claims</code>
     */
    private static Map<String, Object> claims(String issuer, String audience) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", audience);
        return claims;
    }

    /**
     * 通过 <code>SaJwtUtil.createToken()</code> 取得当前测试需要的结果。
     *
     * @param loginType 传给 <code>SaJwtUtil.createToken()</code> 的 <code>String</code> 输入
     * @param issuer    传给 <code>SaJwtUtil.createToken()</code> 的 <code>String</code> 输入
     * @param audience  传给 <code>SaJwtUtil.createToken()</code> 的 <code>String</code> 输入
     * @param key       用于在测试上下文或缓存中索引数据的键
     * @return 当前测试使用的结果，取自 <code>SaJwtUtil.createToken(loginType, "sdk-user", "web", 60L, claims(issuer, audience), key)</code>
     */
    private static String sdkToken(String loginType, String issuer, String audience, String key) {
        return SaJwtUtil.createToken(loginType, "sdk-user", "web", 60L, claims(issuer, audience), key);
    }

    /**
     * 测试前依次恢复或安装配置<code>SaManager</code>。
     */
    @BeforeEach
    void setUp() {
        previousConfig = SaManager.getConfig();
        previousLogic = SaManager.stpLogicMap.get(LOGIN_TYPE);
        SaManager.setConfig(new SaTokenConfig().setIsPrint(false).setJwtSecretKey(GLOBAL_KEY));
    }

    /**
     * 测试后依次恢复或安装配置<code>SaManager</code>、注册登录逻辑<code>SaManager</code>。
     */
    @AfterEach
    void restore() {
        SaManager.setConfig(previousConfig);
        SaManager.removeStpLogic(LOGIN_TYPE);
        if (Objects.nonNull(previousLogic)) {
            SaManager.putStpLogic(previousLogic);
        }
    }

    /**
     * 验证<code>firstPayload.get("iss")</code> 等于 <code>ISSUER</code>，<code>firstPayload.get("aud")</code> 等于 <code>AUDIENCE</code>，<code>second.hasExpectedClaims(sdkToken(LOGIN_TYPE, ISSUER, AUDIENCE, GLOBAL_KEY))</code>为假。
     */
    @Test
    void eachInstanceIssuesAndValidatesWithItsConfiguredKey() {
        SaTokenSecurityProperties firstProperties = properties(FIRST_KEY);
        Ddd4jStpLogicJwtForSimple first = new Ddd4jStpLogicJwtForSimple(firstProperties);
        Ddd4jStpLogicJwtForSimple second = new Ddd4jStpLogicJwtForSimple(properties(SECOND_KEY));
        firstProperties.setJwtSecretKey(GLOBAL_KEY);

        Map<String, Object> extraData = claims("caller-issuer", "caller-audience");
        extraData.put("role", "reader");
        String firstToken = first.createTokenValue("user-one", "web", 60L, extraData);
        String secondToken = second.createTokenValue("user-two", "web", 60L, null);

        Map<String, Object> firstPayload = assertDoesNotThrow(
                () -> SaJwtUtil.getPayloads(firstToken, LOGIN_TYPE, FIRST_KEY),
                "The SDK must verify the first token with the configured instance key");
        Map<String, Object> secondPayload = assertDoesNotThrow(
                () -> SaJwtUtil.getPayloads(secondToken, LOGIN_TYPE, SECOND_KEY));
        assertEquals(ISSUER, firstPayload.get("iss"));
        assertEquals(AUDIENCE, firstPayload.get("aud"));
        assertEquals(ISSUER, secondPayload.get("iss"));
        assertEquals(AUDIENCE, secondPayload.get("aud"));
        assertEquals("reader", first.getExtra(firstToken, "role"));
        assertEquals("caller-issuer", extraData.get("iss"));
        assertEquals("caller-audience", extraData.get("aud"));

        assertThrows(SaJwtException.class, () -> SaJwtUtil.getPayloads(firstToken, LOGIN_TYPE, GLOBAL_KEY));
        assertThrows(SaJwtException.class, () -> SaJwtUtil.getPayloads(secondToken, LOGIN_TYPE, GLOBAL_KEY));
        assertTrue(first.hasExpectedClaims(firstToken));
        assertTrue(second.hasExpectedClaims(secondToken));
        assertFalse(first.hasExpectedClaims(secondToken));
        assertFalse(second.hasExpectedClaims(firstToken));
        assertFalse(first.hasExpectedClaims(sdkToken(LOGIN_TYPE, ISSUER, AUDIENCE, GLOBAL_KEY)));
        assertFalse(second.hasExpectedClaims(sdkToken(LOGIN_TYPE, ISSUER, AUDIENCE, GLOBAL_KEY)));
    }

    /**
     * 验证<code>logic.hasExpectedClaims(sdkToken(LOGIN_TYPE, ISSUER, AUDIENCE, FIRST_KEY))</code>为真，<code>logic.hasExpectedClaims(sdkToken(LOGIN_TYPE, "other-issuer", AUDIENCE, FIRST_KEY))</code>为假，<code>logic.hasExpectedClaims(sdkToken("other-realm", ISSUER, AUDIENCE, FIRST_KEY))</code>为假。
     */
    @Test
    void validSignatureStillRequiresExpectedIssuerAudienceAndRealm() {
        Ddd4jStpLogicJwtForSimple logic = new Ddd4jStpLogicJwtForSimple(properties(FIRST_KEY));

        assertTrue(logic.hasExpectedClaims(sdkToken(LOGIN_TYPE, ISSUER, AUDIENCE, FIRST_KEY)));
        assertFalse(logic.hasExpectedClaims(sdkToken(LOGIN_TYPE, "other-issuer", AUDIENCE, FIRST_KEY)));
        assertFalse(logic.hasExpectedClaims(sdkToken(LOGIN_TYPE, ISSUER, "other-audience", FIRST_KEY)));
        assertFalse(logic.hasExpectedClaims(sdkToken("other-realm", ISSUER, AUDIENCE, FIRST_KEY)));
    }
}
