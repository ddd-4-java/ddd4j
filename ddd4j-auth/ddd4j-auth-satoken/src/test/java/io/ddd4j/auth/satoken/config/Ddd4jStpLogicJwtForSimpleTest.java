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

/** \u9a8c\u8bc1 <code>Ddd4jStpLogicJwtForSimple</code> \u5728<code>eachInstanceIssuesAndValidates</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class Ddd4jStpLogicJwtForSimpleTest {

    /** \u6d4b\u8bd5\u767b\u5f55\u548c\u6821\u9a8c\u4f7f\u7528\u7684\u8d26\u53f7\u57df\u3002 */
    private static final String LOGIN_TYPE = "jwt-signing-isolation";
    /** \u6d4b\u8bd5\u4ee4\u724c\u58f0\u660e\u4f7f\u7528\u7684\u7b7e\u53d1\u65b9\u3002 */
    private static final String ISSUER = "test-issuer";
    /** \u6d4b\u8bd5\u4ee4\u724c\u58f0\u660e\u4f7f\u7528\u7684\u63a5\u6536\u65b9\u3002 */
    private static final String AUDIENCE = "test-audience";
    /** \u6784\u9020\u672c\u7ec4\u6d4b\u8bd5\u8f93\u5165\u65f6\u590d\u7528\u7684\u56fa\u5b9a\u952e\u3002 */
    private static final String FIRST_KEY = "test-only-jwt-signing-isolation-first";
    /** \u6784\u9020\u672c\u7ec4\u6d4b\u8bd5\u8f93\u5165\u65f6\u590d\u7528\u7684\u56fa\u5b9a\u952e\u3002 */
    private static final String SECOND_KEY = "test-only-jwt-signing-isolation-second";
    /** \u6784\u9020\u672c\u7ec4\u6d4b\u8bd5\u8f93\u5165\u65f6\u590d\u7528\u7684\u56fa\u5b9a\u952e\u3002 */
    private static final String GLOBAL_KEY = "test-only-jwt-signing-isolation-global";

    /** \u7ec4\u4ef6\u88c5\u914d\u524d\u4fdd\u5b58\u7684\u914d\u7f6e\uff0c\u4f9b\u9000\u51fa\u65f6\u6062\u590d\u3002 */
    private SaTokenConfig previousConfig;
    /** \u88ab\u6d4b\u6ce8\u518c\u5b89\u88c5\u524d\u7684\u539f\u751f\u767b\u5f55\u903b\u8f91\u3002 */
    private StpLogic previousLogic;

    /**
     * \u8bfb\u53d6 <code>properties</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param key \u7528\u4e8e\u5728\u6d4b\u8bd5\u4e0a\u4e0b\u6587\u6216\u7f13\u5b58\u4e2d\u7d22\u5f15\u6570\u636e\u7684\u952e
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>properties</code>
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
     * \u8bfb\u53d6 <code>claims</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param issuer \u4f20\u7ed9 <code>claims.put()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param audience \u4f20\u7ed9 <code>claims.put()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>claims</code>
     */
    private static Map<String, Object> claims(String issuer, String audience) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", audience);
        return claims;
    }

    /**
     * \u901a\u8fc7 <code>SaJwtUtil.createToken()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param loginType \u4f20\u7ed9 <code>SaJwtUtil.createToken()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param issuer \u4f20\u7ed9 <code>SaJwtUtil.createToken()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param audience \u4f20\u7ed9 <code>SaJwtUtil.createToken()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param key \u7528\u4e8e\u5728\u6d4b\u8bd5\u4e0a\u4e0b\u6587\u6216\u7f13\u5b58\u4e2d\u7d22\u5f15\u6570\u636e\u7684\u952e
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>SaJwtUtil.createToken(loginType, "sdk-user", "web", 60L, claims(issuer, audience), key)</code>
     */
    private static String sdkToken(String loginType, String issuer, String audience, String key) {
        return SaJwtUtil.createToken(loginType, "sdk-user", "web", 60L, claims(issuer, audience), key);
    }

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u6062\u590d\u6216\u5b89\u88c5\u914d\u7f6e<code>SaManager</code>\u3002 */
    @BeforeEach
    void setUp() {
        previousConfig = SaManager.getConfig();
        previousLogic = SaManager.stpLogicMap.get(LOGIN_TYPE);
        SaManager.setConfig(new SaTokenConfig().setIsPrint(false).setJwtSecretKey(GLOBAL_KEY));
    }

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6062\u590d\u6216\u5b89\u88c5\u914d\u7f6e<code>SaManager</code>\u3001\u6ce8\u518c\u767b\u5f55\u903b\u8f91<code>SaManager</code>\u3002 */
    @AfterEach
    void restore() {
        SaManager.setConfig(previousConfig);
        SaManager.removeStpLogic(LOGIN_TYPE);
        if (Objects.nonNull(previousLogic)) {
            SaManager.putStpLogic(previousLogic);
        }
    }

    /** \u9a8c\u8bc1<code>firstPayload.get("iss")</code> \u7b49\u4e8e <code>ISSUER</code>\uff0c<code>firstPayload.get("aud")</code> \u7b49\u4e8e <code>AUDIENCE</code>\uff0c<code>second.hasExpectedClaims(sdkToken(LOGIN_TYPE, ISSUER, AUDIENCE, GLOBAL_KEY))</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>logic.hasExpectedClaims(sdkToken(LOGIN_TYPE, ISSUER, AUDIENCE, FIRST_KEY))</code>\u4e3a\u771f\uff0c<code>logic.hasExpectedClaims(sdkToken(LOGIN_TYPE, "other-issuer", AUDIENCE, FIRST_KEY))</code>\u4e3a\u5047\uff0c<code>logic.hasExpectedClaims(sdkToken("other-realm", ISSUER, AUDIENCE, FIRST_KEY))</code>\u4e3a\u5047\u3002 */
    @Test
    void validSignatureStillRequiresExpectedIssuerAudienceAndRealm() {
        Ddd4jStpLogicJwtForSimple logic = new Ddd4jStpLogicJwtForSimple(properties(FIRST_KEY));

        assertTrue(logic.hasExpectedClaims(sdkToken(LOGIN_TYPE, ISSUER, AUDIENCE, FIRST_KEY)));
        assertFalse(logic.hasExpectedClaims(sdkToken(LOGIN_TYPE, "other-issuer", AUDIENCE, FIRST_KEY)));
        assertFalse(logic.hasExpectedClaims(sdkToken(LOGIN_TYPE, ISSUER, "other-audience", FIRST_KEY)));
        assertFalse(logic.hasExpectedClaims(sdkToken("other-realm", ISSUER, AUDIENCE, FIRST_KEY)));
    }
}
