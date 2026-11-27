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
package io.ddd4j.auth.satoken.handler;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.mock.SaRequestForMock;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.auth.satoken.SaTempToken;
import io.ddd4j.auth.satoken.annotation.SaMixCheckLogin;
import io.ddd4j.auth.satoken.util.SaTempKit;
import io.ddd4j.auth.satoken.util.StpKit;
import io.ddd4j.core.constant.AuthConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AnnotatedElement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class SaMixCheckLoginHandlerTest {

    /** \u5f53\u524d\u7528\u4f8b\u5b9e\u9645\u8c03\u7528\u6216\u914d\u7f6e\u884c\u4e3a\u7684\u5904\u7406\u5668\u3002 */
    private final SaMixCheckLoginHandler handler = new SaMixCheckLoginHandler();

    /** \u8fdb\u5165\u6d4b\u8bd5\u573a\u666f\u524d\u6ce8\u518c\u7684\u914d\u7f6e\u3002 */
    private SaTokenConfig originalConfig;
    /** \u8fdb\u5165\u6d4b\u8bd5\u573a\u666f\u524d\u6ce8\u518c\u7684\u4f1a\u8bdd\u5b58\u50a8\u3002 */
    private SaTokenDao originalDao;
    /** \u8fdb\u5165\u6d4b\u8bd5\u573a\u666f\u524d\u6ce8\u518c\u7684\u8ba4\u8bc1\u903b\u8f91\u3002 */
    private StpLogic originalLogic;

    /**
     * \u901a\u8fc7 <code>Fixture.class.getDeclaredMethod()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param methodName \u4f20\u7ed9 <code>Fixture.class.getDeclaredMethod()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Fixture.class.getDeclaredMethod(methodName).getAnnotation(SaMixCheckLogin.class)</code>
     * @throws NoSuchMethodException \u53cd\u5c04\u5b9a\u4f4d\u88ab\u6d4b\u65b9\u6cd5\u5931\u8d25
     */
    private static SaMixCheckLogin annotation(String methodName) throws NoSuchMethodException {
        return Fixture.class.getDeclaredMethod(methodName).getAnnotation(SaMixCheckLogin.class);
    }

    /**
     * \u901a\u8fc7 <code>Fixture.class.getDeclaredMethod()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param methodName \u4f20\u7ed9 <code>Fixture.class.getDeclaredMethod()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Fixture.class.getDeclaredMethod(methodName)</code>
     * @throws NoSuchMethodException \u53cd\u5c04\u5b9a\u4f4d\u88ab\u6d4b\u65b9\u6cd5\u5931\u8d25
     */
    private static AnnotatedElement element(String methodName) throws NoSuchMethodException {
        return Fixture.class.getDeclaredMethod(methodName);
    }

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u6062\u590d\u6216\u5b89\u88c5\u914d\u7f6e<code>SaManager</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u539f\u751f\u5b58\u50a8<code>SaManager</code>\u3002 */
    @BeforeEach
    void setUp() {
        originalConfig = SaManager.getConfig();
        originalDao = SaManager.getSaTokenDao();
        originalLogic = StpUtil.stpLogic;
        SaManager.setConfig(new SaTokenConfig().setIsPrint(false).setIsLog(false));
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        assertEquals("user", StpKit.USER.getLoginType()); // 触发 StpKit 静态初始化，注册 "user" StpLogic
    }

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6e05\u9664\u4e0a\u4e0b\u6587<code>SaTokenContextMockUtil</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u767b\u5f55\u903b\u8f91<code>StpUtil</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u539f\u751f\u5b58\u50a8<code>SaManager</code>\u3001\u6062\u590d\u6216\u5b89\u88c5\u914d\u7f6e<code>SaManager</code>\u3002 */
    @AfterEach
    void tearDown() {
        SaTokenContextMockUtil.clearContext();
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

    /** \u9a8c\u8bc1<code>handler.getHandlerAnnotationClass()</code> \u7b49\u4e8e <code>SaMixCheckLogin.class</code>\u3002 */
    @Test
    void getHandlerAnnotationClassIsSaMixCheckLogin() {
        assertEquals(SaMixCheckLogin.class, handler.getHandlerAnnotationClass());
    }

    /**
     * \u9a8c\u8bc1<code>handler.checkMethod( annotation("mixLoginOnly"), element("mixLoginOnly"))</code> \u629b\u51fa <code>SaTokenException.class</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void checkWithoutTempTokenRequiresLogin() throws Exception {
        SaTokenContextMockUtil.setMockContext();
        assertThrows(SaTokenException.class, () -> handler.checkMethod(
                annotation("mixLoginOnly"), element("mixLoginOnly")));
    }

    /**
     * \u9a8c\u8bc1<code>StpUtil.isLogin()</code>\u4e3a\u5047\uff0c<code>SaTempKit.getTimeout(tempToken)</code> \u7b49\u4e8e <code>-2</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void checkWithValidTempTokenLogsInAndClearsTokenForThrowaway() throws Exception {
        String tempToken = SaTempKit.createToken(tempToken(), 60);
        SaTokenContextMockUtil.setMockContext();
        SaRequestForMock request = (SaRequestForMock) SaHolder.getRequest();
        request.parameterMap.put(AuthConstants.PARAM_TEMP_TOKEN, tempToken);

        handler.checkMethod(annotation("mixLoginThrowaway"), element("mixLoginThrowaway"));

        // throwaway 模式：用完即弃，不建立登录会话
        assertFalse(StpUtil.isLogin());
        assertEquals(-2, SaTempKit.getTimeout(tempToken));
    }

    /**
     * \u9a8c\u8bc1<code>StpUtil.isLogin()</code>\u4e3a\u771f\uff0c<code>SaTempKit.getTimeout(tempToken) &gt; 0</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void checkWithValidTempTokenAndPersistentTokenKeepsIt() throws Exception {
        String tempToken = SaTempKit.createToken(tempToken(), 60);
        SaTokenContextMockUtil.setMockContext();
        SaRequestForMock request = (SaRequestForMock) SaHolder.getRequest();
        request.parameterMap.put(AuthConstants.PARAM_TEMP_TOKEN, tempToken);

        handler.checkMethod(annotation("mixLoginPersistent"), element("mixLoginPersistent"));

        assertTrue(StpUtil.isLogin());
        assertTrue(SaTempKit.getTimeout(tempToken) > 0);
    }

    /**
     * \u9a8c\u8bc1<code>handler.checkMethod( annotation("mixLoginPersistent"), element("mixLoginPersistent"))</code> \u629b\u51fa <code>SaTokenException.class</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void checkRejectsExpiredTempToken() throws Exception {
        String tempToken = SaTempKit.createToken(tempToken(), 60);
        SaTempKit.deleteToken(tempToken);
        SaTokenContextMockUtil.setMockContext();
        SaRequestForMock request = (SaRequestForMock) SaHolder.getRequest();
        request.parameterMap.put(AuthConstants.PARAM_TEMP_TOKEN, tempToken);

        assertThrows(SaTokenException.class, () -> handler.checkMethod(
                annotation("mixLoginPersistent"), element("mixLoginPersistent")));
    }

    /**
     * \u9a8c\u8bc1<code>handler.checkMethod( annotation("mixLoginPersistent"), element("mixLoginPersistent"))</code> \u629b\u51fa <code>SaTokenException.class</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void checkRejectsTempTokenWithoutLoginId() throws Exception {
        SaTempToken noLoginId = new SaTempToken();
        noLoginId.setDeviceType("PC");
        String tempToken = SaTempKit.createToken(noLoginId, 60);
        SaTokenContextMockUtil.setMockContext();
        SaRequestForMock request = (SaRequestForMock) SaHolder.getRequest();
        request.parameterMap.put(AuthConstants.PARAM_TEMP_TOKEN, tempToken);

        assertThrows(SaTokenException.class, () -> handler.checkMethod(
                annotation("mixLoginPersistent"), element("mixLoginPersistent")));
    }

    /** \u9a8c\u8bc1<code>payload.get(AuthConstants.JWT_AUTH_TYPE)</code> \u7b49\u4e8e <code>"sms"</code>\uff0c<code>payload.get(AuthConstants.JWT_SUBJECT)</code> \u7b49\u4e8e <code>"user-1"</code>\uff0c<code>payload.get(AuthConstants.JWT_ISSUED_AT)</code>\u975e\u7a7a\u3002 */
    @Test
    void getTokenPayloadExtractsTokenFields() {
        Map<String, Object> payload = handler.getTokenPayload(tempToken());

        assertEquals("sms", payload.get(AuthConstants.JWT_AUTH_TYPE));
        assertEquals("user-1", payload.get(AuthConstants.JWT_SUBJECT));
        assertNotNull(payload.get(AuthConstants.JWT_ISSUED_AT));
    }

    /** \u9a8c\u8bc1<code>payload.get(AuthConstants.FIELD_DEVICE_TYPE)</code> \u7b49\u4e8e <code>"PC"</code>\uff0c<code>payload.get(AuthConstants.FIELD_DEVICE_ID)</code> \u7b49\u4e8e <code>"device-1"</code>\uff0c<code>payload.get(AuthConstants.FIELD_APP_VERSION)</code> \u7b49\u4e8e <code>"1.2.3"</code>\u3002 */
    @Test
    void getTerminalPayloadExtractsDeviceFields() {
        Map<String, Object> payload = handler.getTerminalPayload(tempToken());

        assertEquals("PC", payload.get(AuthConstants.FIELD_DEVICE_TYPE));
        assertEquals("device-1", payload.get(AuthConstants.FIELD_DEVICE_ID));
        assertEquals("appstore", payload.get(AuthConstants.FIELD_APP_CHANNEL));
        assertEquals("1.2.3", payload.get(AuthConstants.FIELD_APP_VERSION));
    }

    /**
     * \u9a8c\u8bc1<code>StpUtil.isLogin()</code>\u4e3a\u5047\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void checkIgnoresLoginFlagWhenTempTokenPresentButLoginDisabled() throws Exception {
        String tempToken = SaTempKit.createToken(tempToken(), 60);
        SaTokenContextMockUtil.setMockContext();
        SaRequestForMock request = (SaRequestForMock) SaHolder.getRequest();
        request.parameterMap.put(AuthConstants.PARAM_TEMP_TOKEN, tempToken);

        handler.checkMethod(annotation("mixLoginDisabled"), element("mixLoginDisabled"));

        assertFalse(StpUtil.isLogin());
    }

    /** \u63d0\u4f9b <code>mixLoginOnly()</code>\u3001<code>mixLoginThrowaway()</code>\u3001<code>mixLoginPersistent()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    @SuppressWarnings("unused")
    /**
     * 混合登录校验注解声明夹具：承载不同登录/用后即弃组合的占位端点，供反射读取注解使用。
     *
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     */
    private static class Fixture {

        /**
         * mixLoginOnly() 方法。
         */
        @SaMixCheckLogin
        public void mixLoginOnly() {
        }

        /**
         * mixLoginThrowaway() 方法。
         */
        @SaMixCheckLogin(login = true, throwaway = true)
        public void mixLoginThrowaway() {
        }

        /**
         * mixLoginPersistent() 方法。
         */
        @SaMixCheckLogin(login = true, throwaway = false)
        public void mixLoginPersistent() {
        }

        /**
         * mixLoginDisabled() 方法。
         */
        @SaMixCheckLogin(login = false, throwaway = false)
        public void mixLoginDisabled() {
        }
    }
}
