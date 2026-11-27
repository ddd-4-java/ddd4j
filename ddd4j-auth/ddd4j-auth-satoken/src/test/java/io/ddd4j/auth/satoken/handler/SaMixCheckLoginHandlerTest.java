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
 * 验证 <code>SaMixCheckLoginHandler</code> 在<code>check</code>、<code>checkIgnoresLoginFlag</code>等操作中的行为契约。
 */
class SaMixCheckLoginHandlerTest {

    /**
     * 当前用例实际调用或配置行为的处理器。
     */
    private final SaMixCheckLoginHandler handler = new SaMixCheckLoginHandler();

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
     * 通过 <code>Fixture.class.getDeclaredMethod()</code> 取得当前测试需要的结果。
     *
     * @param methodName 传给 <code>Fixture.class.getDeclaredMethod()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>Fixture.class.getDeclaredMethod(methodName).getAnnotation(SaMixCheckLogin.class)</code>
     * @throws NoSuchMethodException 反射定位被测方法失败
     */
    private static SaMixCheckLogin annotation(String methodName) throws NoSuchMethodException {
        return Fixture.class.getDeclaredMethod(methodName).getAnnotation(SaMixCheckLogin.class);
    }

    /**
     * 通过 <code>Fixture.class.getDeclaredMethod()</code> 取得当前测试需要的结果。
     *
     * @param methodName 传给 <code>Fixture.class.getDeclaredMethod()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>Fixture.class.getDeclaredMethod(methodName)</code>
     * @throws NoSuchMethodException 反射定位被测方法失败
     */
    private static AnnotatedElement element(String methodName) throws NoSuchMethodException {
        return Fixture.class.getDeclaredMethod(methodName);
    }

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
        assertEquals("user", StpKit.USER.getLoginType()); // 触发 StpKit 静态初始化，注册 "user" StpLogic
    }

    /**
     * 测试后依次清除上下文<code>SaTokenContextMockUtil</code>、恢复或安装登录逻辑<code>StpUtil</code>、恢复或安装原生存储<code>SaManager</code>、恢复或安装配置<code>SaManager</code>。
     */
    @AfterEach
    void tearDown() {
        SaTokenContextMockUtil.clearContext();
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
     * 验证<code>handler.getHandlerAnnotationClass()</code> 等于 <code>SaMixCheckLogin.class</code>。
     */
    @Test
    void getHandlerAnnotationClassIsSaMixCheckLogin() {
        assertEquals(SaMixCheckLogin.class, handler.getHandlerAnnotationClass());
    }

    /**
     * 验证<code>handler.checkMethod( annotation("mixLoginOnly"), element("mixLoginOnly"))</code> 抛出 <code>SaTokenException.class</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void checkWithoutTempTokenRequiresLogin() throws Exception {
        SaTokenContextMockUtil.setMockContext();
        assertThrows(SaTokenException.class, () -> handler.checkMethod(
                annotation("mixLoginOnly"), element("mixLoginOnly")));
    }

    /**
     * 验证<code>StpUtil.isLogin()</code>为假，<code>SaTempKit.getTimeout(tempToken)</code> 等于 <code>-2</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
     * 验证<code>StpUtil.isLogin()</code>为真，<code>SaTempKit.getTimeout(tempToken) &gt; 0</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
     * 验证<code>handler.checkMethod( annotation("mixLoginPersistent"), element("mixLoginPersistent"))</code> 抛出 <code>SaTokenException.class</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
     * 验证<code>handler.checkMethod( annotation("mixLoginPersistent"), element("mixLoginPersistent"))</code> 抛出 <code>SaTokenException.class</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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

    /**
     * 验证<code>payload.get(AuthConstants.JWT_AUTH_TYPE)</code> 等于 <code>"sms"</code>，<code>payload.get(AuthConstants.JWT_SUBJECT)</code> 等于 <code>"user-1"</code>，<code>payload.get(AuthConstants.JWT_ISSUED_AT)</code>非空。
     */
    @Test
    void getTokenPayloadExtractsTokenFields() {
        Map<String, Object> payload = handler.getTokenPayload(tempToken());

        assertEquals("sms", payload.get(AuthConstants.JWT_AUTH_TYPE));
        assertEquals("user-1", payload.get(AuthConstants.JWT_SUBJECT));
        assertNotNull(payload.get(AuthConstants.JWT_ISSUED_AT));
    }

    /**
     * 验证<code>payload.get(AuthConstants.FIELD_DEVICE_TYPE)</code> 等于 <code>"PC"</code>，<code>payload.get(AuthConstants.FIELD_DEVICE_ID)</code> 等于 <code>"device-1"</code>，<code>payload.get(AuthConstants.FIELD_APP_VERSION)</code> 等于 <code>"1.2.3"</code>。
     */
    @Test
    void getTerminalPayloadExtractsDeviceFields() {
        Map<String, Object> payload = handler.getTerminalPayload(tempToken());

        assertEquals("PC", payload.get(AuthConstants.FIELD_DEVICE_TYPE));
        assertEquals("device-1", payload.get(AuthConstants.FIELD_DEVICE_ID));
        assertEquals("appstore", payload.get(AuthConstants.FIELD_APP_CHANNEL));
        assertEquals("1.2.3", payload.get(AuthConstants.FIELD_APP_VERSION));
    }

    /**
     * 验证<code>StpUtil.isLogin()</code>为假。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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

    /**
     * 提供 <code>mixLoginOnly()</code>、<code>mixLoginThrowaway()</code>、<code>mixLoginPersistent()</code> 等供当前测试使用的独立操作。
     */
    @SuppressWarnings("unused")
    private static class Fixture {

        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         */
        @SaMixCheckLogin
        public void mixLoginOnly() {
        }

        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         */
        @SaMixCheckLogin(login = true, throwaway = true)
        public void mixLoginThrowaway() {
        }

        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         */
        @SaMixCheckLogin(login = true, throwaway = false)
        public void mixLoginPersistent() {
        }

        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         */
        @SaMixCheckLogin(login = false, throwaway = false)
        public void mixLoginDisabled() {
        }
    }
}
