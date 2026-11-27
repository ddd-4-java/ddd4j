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
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import cn.dev33.satoken.json.SaJsonTemplate;
import cn.dev33.satoken.json.SaJsonTemplateForJackson;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.auth.satoken.config.SaTokenAuthenticationMode;
import io.ddd4j.auth.satoken.config.SaTokenSecurityConfigurer;
import io.ddd4j.auth.satoken.config.SaTokenSecurityProperties;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.auth.session.AuthSessionConfig;
import io.ddd4j.core.exception.NotLoggedInException;
import io.ddd4j.core.subject.Subject;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** \u901a\u8fc7 <code>SaManager.getConfig()</code>\u3001<code>SaManager.getSaTokenDao()</code>\u3001<code>SaManager.getSaJsonTemplate()</code> \u9a8c\u8bc1 <code>SaTokenSubjectRedis</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
@EnabledIfSystemProperty(named = "ddd4j.test.redis.port", matches = "[0-9]+")
class SaTokenSubjectRedisTest {

    /**
     * \u9a8c\u8bc1<code>adminToken</code> \u7b49\u4e8e <code>"redis-admin-token"</code>\uff0c<code>adminToken</code>\u975e\u7a7a\u3002
     * @param mode \u4f20\u7ed9 <code>properties.setAuthenticationMode()</code> \u7684 <code>SaTokenAuthenticationMode</code> \u8f93\u5165
     */
    @ParameterizedTest
    @EnumSource(SaTokenAuthenticationMode.class)
    void realRedisRetainsRealmIsolationThroughLoginRenewalAndLogout(SaTokenAuthenticationMode mode) {
        SaTokenConfig previousConfig = SaManager.getConfig();
        SaTokenDao previousDao = SaManager.getSaTokenDao();
        SaJsonTemplate previousJson = SaManager.getSaJsonTemplate();
        StpLogic previousLogic = StpUtil.stpLogic;
        LettuceConnectionFactory connection = new LettuceConnectionFactory(
                "127.0.0.1", Integer.getInteger("ddd4j.test.redis.port"));
        String namespace = "ddd4j-test-" + UUID.randomUUID();
        String prefix = namespace + "-token";
        String adminRealm = "admin-" + UUID.randomUUID();
        String userRealm = "user-" + UUID.randomUUID();
        connection.afterPropertiesSet();
        SaTokenDaoForRedisTemplate dao = new SaTokenDaoForRedisTemplate();
        dao.init(connection);

        try {
            SaManager.setSaTokenDao(dao);
            SaManager.setSaJsonTemplate(new SaJsonTemplateForJackson());
            SaManager.setConfig(new SaTokenConfig().setIsPrint(false));
            SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
            properties.setTokenNamespace(namespace);
            properties.setLoginType(adminRealm);
            properties.setTokenTtlSeconds(180);
            properties.setActiveTimeoutSeconds(90);
            properties.setAuthenticationMode(mode);
            if (mode == SaTokenAuthenticationMode.JWT_SIMPLE) {
                properties.setJwtSecretKey("a-redis-regression-signing-secret-of-32-bytes");
                properties.setJwtAudience("test-client");
                properties.setJwtIssuer("test-service");
            }
            SaTokenSecurityConfigurer.configure(properties);

            SaTokenSubjectProvider provider = new SaTokenSubjectProvider();
            Subject admin = provider.getSubject(adminRealm);
            Subject user = provider.getSubject(userRealm);
            SaTokenContextMockUtil.setMockContext();
            String preset = mode == SaTokenAuthenticationMode.SESSION ? "redis-admin-token" : null;
            String adminToken = admin.login(loginRequest("administrator", preset));
            SaTokenContextMockUtil.setMockContext();
            String userToken = user.login(loginRequest("customer", "redis-user-token"));

            String adminKeyPrefix = prefix + ":" + adminRealm + ":";
            String userKeyPrefix = prefix + ":" + userRealm + ":";
            String mappingKey = adminKeyPrefix + "token:" + adminToken;
            String activeKey = adminKeyPrefix + "last-active:" + adminToken;
            String sessionKey = adminKeyPrefix + "session:shared";
            String tokenSessionKey = adminKeyPrefix + "token-session:" + adminToken;
            String userMappingKey = userKeyPrefix + "token:" + userToken;

            if (mode == SaTokenAuthenticationMode.SESSION) {
                assertEquals("redis-admin-token", adminToken);
            }
            assertNotNull(adminToken);
            assertEquals("shared", dao.get(mappingKey));
            assertEquals("shared", dao.get(userMappingKey));
            assertEquals("administrator", admin.getPrincipalByLoginId("shared").getUserId());
            assertEquals("administrator", admin.verify(adminToken).getUserId());
            assertEquals("customer", user.verify(userToken).getUserId());
            assertNull(admin.verify(userToken));
            assertNull(user.verify(adminToken));
            assertNotNull(dao.get(tokenSessionKey));
            assertNotNull(dao.get(sessionKey));
            assertTtl(dao, mappingKey, 45);
            assertTtl(dao, sessionKey, 45);
            assertTtl(dao, tokenSessionKey, 45);
            assertTtl(dao, activeKey, 45);

            SaTokenContextMockUtil.setMockContext();
            SaManager.getStpLogic(adminRealm).setTokenValueToStorage(adminToken);
            String activity = dao.get(activeKey);
            assertNotNull(activity);
            assertEquals(adminToken, admin.refresh());
            assertEquals(activity, dao.get(activeKey));
            assertTtl(dao, mappingKey, 180);
            assertTtl(dao, sessionKey, 180);
            assertTtl(dao, tokenSessionKey, 180);
            assertTtl(dao, activeKey, 180);
            assertTtl(dao, userMappingKey, 45);

            admin.logout();
            assertNull(admin.verify(adminToken));
            assertNull(dao.get(mappingKey));
            assertNull(dao.get(activeKey));
            assertNull(dao.get(sessionKey));
            assertNull(dao.get(tokenSessionKey));
            assertNotNull(user.verify(userToken));

            SaManager.getStpLogic(adminRealm).setTokenValueToStorage(adminToken);
            assertThrows(NotLoggedInException.class, admin::refresh);
        } finally {
            SaTokenContextMockUtil.clearContext();
            try {
                for (String key : dao.searchData(prefix + ":", "", 0, -1, true)) {
                    dao.delete(key);
                }
            } finally {
                StpUtil.setStpLogic(previousLogic);
                SaManager.removeStpLogic(adminRealm);
                SaManager.removeStpLogic(userRealm);
                SaManager.setSaTokenDao(previousDao);
                SaManager.setSaJsonTemplate(previousJson);
                SaManager.setConfig(previousConfig);
                connection.destroy();
            }
        }
    }

    /**
     * \u6784\u9020 <code>AuthPrincipal</code>\u3001<code>AuthSessionConfig</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param userId \u4f20\u7ed9 <code>AuthRequest.of()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>AuthRequest</code> \u7ed3\u679c
     */
    private AuthRequest loginRequest(String userId, String token) {
        return AuthRequest.of("shared")
                .setPrincipal(new AuthPrincipal().setLoginId("shared").setUserId(userId))
                .setSessionConfig(new AuthSessionConfig()
                        .setTimeout(45).setDeviceId("test-device")
                        .setCreateTokenSessionNow(true).setPresetToken(token));
    }

    /**
     * \u6838\u5bf9<code>ttl &gt; timeout - 5 &amp;&amp; ttl &lt;= timeout</code>\u4e3a\u771f\u3002
     * @param dao \u4f20\u7ed9 <code>dao.getTimeout()</code> \u7684 <code>SaTokenDao</code> \u8f93\u5165
     * @param key \u7528\u4e8e\u5728\u6d4b\u8bd5\u4e0a\u4e0b\u6587\u6216\u7f13\u5b58\u4e2d\u7d22\u5f15\u6570\u636e\u7684\u952e
     * @param timeout \u7b49\u5f85\u6d4b\u8bd5\u4e8b\u4ef6\u7684\u8d85\u65f6\u65f6\u957f
     */
    private void assertTtl(SaTokenDao dao, String key, long timeout) {
        long ttl = dao.getTimeout(key);
        assertTrue(ttl > timeout - 5 && ttl <= timeout, () -> key + " has unexpected TTL " + ttl);
    }
}
