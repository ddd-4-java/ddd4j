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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class SaTempKitTest {

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
        SaManager.setConfig(new SaTokenConfig().setIsPrint(false).setIsLog(false));
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
    }

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6062\u590d\u6216\u5b89\u88c5\u767b\u5f55\u903b\u8f91<code>StpUtil</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u539f\u751f\u5b58\u50a8<code>SaManager</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u914d\u7f6e<code>SaManager</code>\u3002 */
    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(originalLogic);
        SaManager.setSaTokenDao(originalDao);
        SaManager.setConfig(originalConfig);
    }

    /**
     * \u8bfb\u53d6 <code>token</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>token</code>
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

    /** \u9a8c\u8bc1<code>parsed</code>\u975e\u7a7a\uff0c<code>parsed.getLoginId()</code> \u7b49\u4e8e <code>"user-1"</code>\uff0c<code>parsed.getAuthType()</code> \u7b49\u4e8e <code>"sms"</code>\u3002 */
    @Test
    void createAndParseTokenRoundTrips() {
        SaTempToken value = tempToken();
        String token = SaTempKit.createToken(value, 60);

        SaTempToken parsed = SaTempKit.parseToken(token);

        assertNotNull(parsed);
        assertEquals("user-1", parsed.getLoginId());
        assertEquals("sms", parsed.getAuthType());
    }

    /** \u9a8c\u8bc1<code>SaTempKit.parseToken("custom-service", token)</code> \u629b\u51fa <code>SaTokenException.class</code>\u3002 */
    @Test
    void parseTokenWithServiceRejectsPlainToken() {
        String token = SaTempKit.createToken(tempToken(), 60);

        assertThrows(SaTokenException.class, () -> SaTempKit.parseToken("custom-service", token));
    }

    /** \u9a8c\u8bc1<code>timeout &lt;= 60 &amp;&amp; timeout &gt; 0</code>\u4e3a\u771f\u3002 */
    @Test
    void getTimeoutReflectsRemainingLifetime() {
        String token = SaTempKit.createToken(tempToken(), 60);

        long timeout = SaTempKit.getTimeout(token);

        assertTrue(timeout <= 60 && timeout > 0);
    }

    /** \u9a8c\u8bc1<code>SaTempKit.getTimeout(token)</code> \u7b49\u4e8e <code>-2</code>\u3002 */
    @Test
    void deleteTokenInvalidatesIt() {
        String token = SaTempKit.createToken(tempToken(), 60);
        SaTempKit.deleteToken(token);

        assertEquals(-2, SaTempKit.getTimeout(token));
    }

    /** \u9a8c\u8bc1<code>checked.getLoginId()</code> \u7b49\u4e8e <code>"user-1"</code>\u3002 */
    @Test
    void checkTempTokenValidatesStringToken() {
        String token = SaTempKit.createToken(tempToken(), 60);

        SaTempToken checked = SaTempKit.checkTempToken(token);

        assertEquals("user-1", checked.getLoginId());
    }

    /** \u9a8c\u8bc1<code>SaTempKit.checkTempToken((String) null)</code> \u629b\u51fa <code>SaTokenException.class</code>\uff0c<code>SaTempKit.checkTempToken(" ")</code> \u629b\u51fa <code>SaTokenException.class</code>\u3002 */
    @Test
    void checkTempTokenRejectsBlankToken() {
        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken((String) null));
        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken("  "));
    }

    /** \u9a8c\u8bc1<code>SaTempKit.checkTempToken(token)</code> \u629b\u51fa <code>SaTokenException.class</code>\u3002 */
    @Test
    void checkTempTokenRejectsExpiredToken() {
        String token = SaTempKit.createToken(tempToken(), 60);
        SaTempKit.deleteToken(token);

        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken(token));
    }

    /** \u9a8c\u8bc1<code>SaTempKit.checkTempToken(token)</code> \u629b\u51fa <code>SaTokenException.class</code>\u3002 */
    @Test
    void checkTempTokenRejectsMissingLoginId() {
        SaTempToken noLoginId = new SaTempToken();
        noLoginId.setDeviceType("PC");
        String token = SaTempKit.createToken(noLoginId, 60);

        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken(token));
    }

    /** \u9a8c\u8bc1<code>SaTempKit.checkTempToken(value)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>value</code>\u3002 */
    @Test
    void checkTempTokenValidatesParsedObject() {
        SaTempToken value = tempToken();

        assertSame(value, SaTempKit.checkTempToken(value));
    }

    /** \u9a8c\u8bc1<code>SaTempKit.checkTempToken((SaTempToken) null)</code> \u629b\u51fa <code>SaTokenException.class</code>\u3002 */
    @Test
    void checkTempTokenRejectsNullParsedObject() {
        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken((SaTempToken) null));
    }

    /** \u9a8c\u8bc1<code>SaTempKit.checkTempToken(noLoginId)</code> \u629b\u51fa <code>SaTokenException.class</code>\u3002 */
    @Test
    void checkTempTokenRejectsParsedObjectWithoutLoginId() {
        SaTempToken noLoginId = new SaTempToken();
        noLoginId.setDeviceId("device-1");

        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken(noLoginId));
    }
}
