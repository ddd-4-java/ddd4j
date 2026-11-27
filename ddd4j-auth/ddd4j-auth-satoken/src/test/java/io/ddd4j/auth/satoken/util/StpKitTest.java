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
package io.ddd4j.auth.satoken.util;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import io.ddd4j.auth.satoken.SaConstants;
import io.ddd4j.auth.satoken.config.SaTokenAuthenticationMode;
import io.ddd4j.auth.satoken.config.SaTokenSecurityConfigurer;
import io.ddd4j.auth.satoken.config.SaTokenSecurityProperties;
import io.ddd4j.core.constant.AuthConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>StpKit</code> \u5728<code>commonPayloadConstantsResolveClaims</code>\u3001<code>typedIdAccessorsPreferDocumentedClaims</code>\u3001<code>infoIdStringAndIntegerReturnNull</code>\u3001<code>infoIdLong</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class StpKitTest {

    /** \u8fdb\u5165\u6d4b\u8bd5\u573a\u666f\u524d\u6ce8\u518c\u7684\u914d\u7f6e\u3002 */
    private SaTokenConfig originalConfig;
    /** \u8fdb\u5165\u6d4b\u8bd5\u573a\u666f\u524d\u6ce8\u518c\u7684\u4f1a\u8bdd\u5b58\u50a8\u3002 */
    private SaTokenDao originalDao;
    /** \u8fdb\u5165\u6d4b\u8bd5\u573a\u666f\u524d\u6ce8\u518c\u7684\u8ba4\u8bc1\u903b\u8f91\u3002 */
    private StpLogic originalLogic;

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u6062\u590d\u6216\u5b89\u88c5\u914d\u7f6e<code>SaManager</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u539f\u751f\u5b58\u50a8<code>SaManager</code>\u3002 */
    @BeforeEach
    void setUp() {
        originalConfig = SaManager.getConfig();
        originalDao = SaManager.getSaTokenDao();
        originalLogic = StpUtil.stpLogic;
        SaManager.setConfig(new SaTokenConfig()
                .setJwtSecretKey("ddd4j-stpkit-test-secret-32bytes")
                .setIsPrint(false)
                .setIsLog(false));
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        cn.dev33.satoken.context.mock.SaTokenContextMockUtil.setMockContext();
        // extra 扩展参数只有在 JWT Simple 逻辑下可用
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        properties.setJwtSecretKey("ddd4j-stpkit-test-secret-32bytes");
        properties.setJwtIssuer("ddd4j-test");
        properties.setJwtAudience("ddd4j-tests");
        SaTokenSecurityConfigurer.configure(properties);
    }

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6e05\u9664\u4e0a\u4e0b\u6587<code>cn.dev33.satoken.context.mock.SaTokenContextMockUtil</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u767b\u5f55\u903b\u8f91<code>StpUtil</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u539f\u751f\u5b58\u50a8<code>SaManager</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u914d\u7f6e<code>SaManager</code>\u3002 */
    @AfterEach
    void tearDown() {
        cn.dev33.satoken.context.mock.SaTokenContextMockUtil.clearContext();
        StpUtil.setStpLogic(originalLogic);
        SaManager.setSaTokenDao(originalDao);
        SaManager.setConfig(originalConfig);
    }

    /** \u8c03\u7528 <code>extras.put()</code>\u3001<code>StpUtil.login()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
    private void loginWithExtras() {
        Map<String, Object> extras = new HashMap<>();
        extras.put(AuthConstants.FIELD_USER_ID, 1001L);
        extras.put(AuthConstants.FIELD_ORG_ID, 10L);
        extras.put(AuthConstants.FIELD_ROLE_ID, "role-7");
        extras.put(SaConstants.PAYLOAD_XQ_ORG_ID, 5L);
        extras.put(SaConstants.PAYLOAD_INFO_ID, 42L);
        extras.put(SaConstants.PAYLOAD_SCHOOL_CODE, "110101");
        extras.put(SaConstants.PAYLOAD_IDENTITY_ID, 2);
        extras.put("custom-key", "custom-value");
        StpUtil.login(1001L, new SaLoginParameter().setTimeout(600).setExtraData(extras));
    }

    /** \u9a8c\u8bc1<code>StpKit.getLoginIdAsLong()</code> \u7b49\u4e8e <code>Long.valueOf(1001)</code>\uff0c<code>StpKit.getLoginIdAsString()</code> \u7b49\u4e8e <code>"1001"</code>\uff0c<code>StpKit.getLoginIdAsInteger()</code> \u7b49\u4e8e <code>Integer.valueOf(1001)</code>\u3002 */
    @Test
    void loginIdAccessorsReturnTypedValues() {
        loginWithExtras();

        assertEquals(Long.valueOf(1001), StpKit.getLoginIdAsLong());
        assertEquals("1001", StpKit.getLoginIdAsString());
        assertEquals(Integer.valueOf(1001), StpKit.getLoginIdAsInteger());
    }

    /** \u9a8c\u8bc1<code>((Number) StpKit.getUserId()).longValue()</code> \u7b49\u4e8e <code>1001L</code>\uff0c<code>StpKit.getUserIdAsLong().longValue()</code> \u7b49\u4e8e <code>1001L</code>\uff0c<code>StpKit.getUserIdAsInteger()</code> \u7b49\u4e8e <code>Integer.valueOf(1001)</code>\u3002 */
    @Test
    void userIdAccessorsReadExtraData() {
        loginWithExtras();

        assertEquals(1001L, ((Number) StpKit.getUserId()).longValue());
        assertEquals(1001L, StpKit.getUserIdAsLong().longValue());
        assertEquals("1001", StpKit.getUserIdAsString());
        assertEquals(Integer.valueOf(1001), StpKit.getUserIdAsInteger());
    }

    /** 验证<code>((Number) StpKit.getOrgId()).longValue()</code> 等于 <code>10L</code>，<code>StpKit.getOrgIdAsString()</code> 等于 <code>"10"</code>，<code>StpKit.getRoleIdAsString()</code> 等于 <code>"role-7"</code>。 */
    @Test
    void orgRoleAndSchoolAccessorsReadExtraData() {
        loginWithExtras();

        assertEquals(10L, ((Number) StpKit.getOrgId()).longValue());
        assertEquals("10", StpKit.getOrgIdAsString());
        assertEquals("role-7", StpKit.getRoleIdAsString());
        assertEquals("5", StpKit.getXqOrgIdAsString());
        assertEquals(Integer.valueOf(10), StpKit.getOrgIdAsInteger());
        assertEquals(42L, StpKit.getOrgIdAsLong().longValue());
        assertEquals(Integer.valueOf(5), StpKit.getXqOrgIdAsInteger());
        assertEquals(5L, StpKit.getXqOrgIdAsLong().longValue());
        assertEquals(Integer.valueOf(42), StpKit.getInfoIdAsInteger());
        assertEquals(42L, ((Number) StpKit.getRoleId()).longValue());
        assertEquals(Integer.valueOf(42), StpKit.getInfoIdAsInteger());
        assertEquals(10L, StpKit.getInfoIdAsLong().longValue());
        assertEquals("42", StpKit.getInfoIdAsString());
        assertEquals("110101", StpKit.getXxdmAsString());
        assertEquals(Integer.valueOf(2), StpKit.getIdentityIdAsInteger());
    }

    /** \u9a8c\u8bc1<code>StpKit.getExtraAsString("custom-key")</code> \u7b49\u4e8e <code>"custom-value"</code>\uff0c<code>StpKit.getExtraAsLong("orgId").longValue()</code> \u7b49\u4e8e <code>10L</code>\uff0c<code>StpKit.getExtraAsLong("missing-key")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void getExtraAsAppliesMapperAndTypeConversion() {
        loginWithExtras();

        assertEquals("custom-value", StpKit.getExtraAsString("custom-key"));
        assertEquals(10L, StpKit.getExtraAsLong(AuthConstants.FIELD_ORG_ID).longValue());
        assertEquals("custom-value", StpKit.getExtraAs("custom-key", String.class));
        assertNull(StpKit.getExtraAs("missing-key", String.class));
        assertNull(StpKit.getExtraAsInteger("missing-key"));
        assertNull(StpKit.getExtraAsLong("missing-key"));
    }

    /** \u9a8c\u8bc1<code>kit.getExtraAsString(token, "custom-key")</code> \u7b49\u4e8e <code>"custom-value"</code>\uff0c<code>StpKit.getExtraAsLong(token, "orgId").longValue()</code> \u7b49\u4e8e <code>10L</code>\uff0c<code>StpKit.getExtraAs(token, "missing-key", String.class)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void getExtraAsWithTokenValueReadsSpecificSession() {
        loginWithExtras();
        String token = StpUtil.getTokenValue();
        StpKit kit = new StpKit();

        assertEquals("custom-value", kit.getExtraAsString(token, "custom-key"));
        assertEquals(10L, StpKit.getExtraAsLong(token, AuthConstants.FIELD_ORG_ID).longValue());
        assertEquals("custom-value", StpKit.getExtraAs(token, "custom-key", String.class));
        assertNull(StpKit.getExtraAs(token, "missing-key", String.class));
    }

    /** \u9a8c\u8bc1<code>kit.getExtraAsString(token, "custom-key")</code> \u7b49\u4e8e <code>"custom-value"</code>\uff0c<code>kit.getExtraAsInteger(token, "userId")</code> \u7b49\u4e8e <code>Integer.valueOf(1001)</code>\u3002 */
    @Test
    void instanceExtraAccessorsDelegate() {
        loginWithExtras();
        String token = StpUtil.getTokenValue();
        StpKit kit = new StpKit();

        assertEquals("custom-value", kit.getExtraAsString(token, "custom-key"));
        assertEquals(Integer.valueOf(1001), kit.getExtraAsInteger(token, AuthConstants.FIELD_USER_ID));
    }

    /** \u9a8c\u8bc1<code>StpKit.DEFAULT</code>\u975e\u7a7a\uff0c<code>StpKit.ADMIN</code>\u975e\u7a7a\uff0c<code>SaManager.getStpLogic("xx", false)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>StpKit.XXX</code>\u3002 */
    @Test
    void defaultAndNamedStpLogicsAreAvailable() {
        assertNotNull(StpKit.DEFAULT);
        assertNotNull(StpKit.ADMIN);
        assertNotNull(StpKit.USER);
        assertNotNull(StpKit.XXX);
    }
}
