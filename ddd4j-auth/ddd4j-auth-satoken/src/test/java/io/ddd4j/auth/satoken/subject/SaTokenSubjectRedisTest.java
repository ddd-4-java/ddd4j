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

/**
 * 通过 <code>SaManager.getConfig()</code>、<code>SaManager.getSaTokenDao()</code>、<code>SaManager.getSaJsonTemplate()</code> 验证 <code>SaTokenSubjectRedis</code> 的运行行为。
 */
@EnabledIfSystemProperty(named = "ddd4j.test.redis.port", matches = "[0-9]+")
class SaTokenSubjectRedisTest {

    /**
     * 验证<code>adminToken</code> 等于 <code>"redis-admin-token"</code>，<code>adminToken</code>非空。
     *
     * @param mode 传给 <code>properties.setAuthenticationMode()</code> 的 <code>SaTokenAuthenticationMode</code> 输入
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
     * 构造 <code>AuthPrincipal</code>、<code>AuthSessionConfig</code> 作为当前测试的输入或结果。
     *
     * @param userId 传给 <code>AuthRequest.of()</code> 的 <code>String</code> 输入
     * @param token  从测试登录获得的会话凭据
     * @return 提供给后续契约断言的 <code>AuthRequest</code> 结果
     */
    private AuthRequest loginRequest(String userId, String token) {
        return AuthRequest.of("shared")
                .setPrincipal(new AuthPrincipal().setLoginId("shared").setUserId(userId))
                .setSessionConfig(new AuthSessionConfig()
                        .setTimeout(45).setDeviceId("test-device")
                        .setCreateTokenSessionNow(true).setPresetToken(token));
    }

    /**
     * 核对<code>ttl &gt; timeout - 5 &amp;&amp; ttl &lt;= timeout</code>为真。
     *
     * @param dao     传给 <code>dao.getTimeout()</code> 的 <code>SaTokenDao</code> 输入
     * @param key     用于在测试上下文或缓存中索引数据的键
     * @param timeout 等待测试事件的超时时长
     */
    private void assertTtl(SaTokenDao dao, String key, long timeout) {
        long ttl = dao.getTimeout(key);
        assertTrue(ttl > timeout - 5 && ttl <= timeout, () -> key + " has unexpected TTL " + ttl);
    }
}
