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
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.auth.satoken.SaTempToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 <code>SaTempKit</code> 在<code>parseToken</code>、<code>checkTempToken</code>等操作中的行为契约。
 */
class SaTempKitTest {

    /**
     * 进入测试场景前注册的配置。
     */
    private SaTokenConfig originalConfig;
    /**
     * 进入测试场景前注册的会话存储。
     */
    private SaTokenDao originalDao;
    /**
     * 进入测试场景前注册的认证逻辑。
     */
    private StpLogic originalLogic;

    /**
     * 测试前依次恢复或安装配置<code>SaManager</code>、恢复或安装原生存储<code>SaManager</code>。
     */
    @BeforeEach
    void setUp() {
        originalConfig = SaManager.getConfig();
        originalDao = SaManager.getSaTokenDao();
        originalLogic = StpUtil.stpLogic;
        SaManager.setConfig(new SaTokenConfig().setIsPrint(false).setIsLog(false));
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
    }

    /**
     * 测试后依次恢复或安装登录逻辑<code>StpUtil</code>、恢复或安装原生存储<code>SaManager</code>、恢复或安装配置<code>SaManager</code>。
     */
    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(originalLogic);
        SaManager.setSaTokenDao(originalDao);
        SaManager.setConfig(originalConfig);
    }

    /**
     * 读取 <code>token</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>token</code>
     */
    private SaTempToken tempToken() {
        SaTempToken token = new SaTempToken();
        token.setLoginId("user-1");
        token.setDeviceType("PC");
        token.setDeviceId("device-1");
        token.setAuthType("sms");
        token.setAppId("app-1");
        token.setAppChannel("appstore");
        token.setAppVersion("1.2.3");
        return token;
    }

    /**
     * 验证<code>parsed</code>非空，<code>parsed.getLoginId()</code> 等于 <code>"user-1"</code>，<code>parsed.getAuthType()</code> 等于 <code>"sms"</code>。
     */
    @Test
    void createAndParseTokenRoundTrips() {
        SaTempToken value = tempToken();
        String token = SaTempKit.createToken(value, 60);

        SaTempToken parsed = SaTempKit.parseToken(token);

        assertNotNull(parsed);
        assertEquals("user-1", parsed.getLoginId());
        assertEquals("sms", parsed.getAuthType());
    }

    /**
     * 验证<code>SaTempKit.parseToken("custom-service", token)</code> 抛出 <code>SaTokenException.class</code>。
     */
    @Test
    void parseTokenWithServiceRejectsPlainToken() {
        String token = SaTempKit.createToken(tempToken(), 60);

        assertThrows(SaTokenException.class, () -> SaTempKit.parseToken("custom-service", token));
    }

    /**
     * 验证<code>timeout &lt;= 60 &amp;&amp; timeout &gt; 0</code>为真。
     */
    @Test
    void getTimeoutReflectsRemainingLifetime() {
        String token = SaTempKit.createToken(tempToken(), 60);

        long timeout = SaTempKit.getTimeout(token);

        assertTrue(timeout <= 60 && timeout > 0);
    }

    /**
     * 验证<code>SaTempKit.getTimeout(token)</code> 等于 <code>-2</code>。
     */
    @Test
    void deleteTokenInvalidatesIt() {
        String token = SaTempKit.createToken(tempToken(), 60);
        SaTempKit.deleteToken(token);

        assertEquals(-2, SaTempKit.getTimeout(token));
    }

    /**
     * 验证<code>checked.getLoginId()</code> 等于 <code>"user-1"</code>。
     */
    @Test
    void checkTempTokenValidatesStringToken() {
        String token = SaTempKit.createToken(tempToken(), 60);

        SaTempToken checked = SaTempKit.checkTempToken(token);

        assertEquals("user-1", checked.getLoginId());
    }

    /**
     * 验证<code>SaTempKit.checkTempToken((String) null)</code> 抛出 <code>SaTokenException.class</code>，<code>SaTempKit.checkTempToken(" ")</code> 抛出 <code>SaTokenException.class</code>。
     */
    @Test
    void checkTempTokenRejectsBlankToken() {
        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken((String) null));
        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken("  "));
    }

    /**
     * 验证<code>SaTempKit.checkTempToken(token)</code> 抛出 <code>SaTokenException.class</code>。
     */
    @Test
    void checkTempTokenRejectsExpiredToken() {
        String token = SaTempKit.createToken(tempToken(), 60);
        SaTempKit.deleteToken(token);

        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken(token));
    }

    /**
     * 验证<code>SaTempKit.checkTempToken(token)</code> 抛出 <code>SaTokenException.class</code>。
     */
    @Test
    void checkTempTokenRejectsMissingLoginId() {
        SaTempToken noLoginId = new SaTempToken();
        noLoginId.setDeviceType("PC");
        String token = SaTempKit.createToken(noLoginId, 60);

        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken(token));
    }

    /**
     * 验证<code>SaTempKit.checkTempToken(value)</code> 与…为同一实例 <code>value</code>。
     */
    @Test
    void checkTempTokenValidatesParsedObject() {
        SaTempToken value = tempToken();

        assertSame(value, SaTempKit.checkTempToken(value));
    }

    /**
     * 验证<code>SaTempKit.checkTempToken((SaTempToken) null)</code> 抛出 <code>SaTokenException.class</code>。
     */
    @Test
    void checkTempTokenRejectsNullParsedObject() {
        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken((SaTempToken) null));
    }

    /**
     * 验证<code>SaTempKit.checkTempToken(noLoginId)</code> 抛出 <code>SaTokenException.class</code>。
     */
    @Test
    void checkTempTokenRejectsParsedObjectWithoutLoginId() {
        SaTempToken noLoginId = new SaTempToken();
        noLoginId.setDeviceId("device-1");

        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken(noLoginId));
    }
}
