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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaMixCheckLoginHandlerTest {

    private final SaMixCheckLoginHandler handler = new SaMixCheckLoginHandler();

    private SaTokenConfig originalConfig;
    private SaTokenDao originalDao;
    private StpLogic originalLogic;

    @BeforeEach
    void setUp() {
        originalConfig = SaManager.getConfig();
        originalDao = SaManager.getSaTokenDao();
        originalLogic = StpUtil.stpLogic;
        SaManager.setConfig(new SaTokenConfig().setIsPrint(false).setIsLog(false));
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        assertEquals("user", StpKit.USER.getLoginType()); // 触发 StpKit 静态初始化，注册 "user" StpLogic
    }

    @AfterEach
    void tearDown() {
        SaTokenContextMockUtil.clearContext();
        StpUtil.setStpLogic(originalLogic);
        SaManager.setSaTokenDao(originalDao);
        SaManager.setConfig(originalConfig);
    }

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

    private static SaMixCheckLogin annotation(String methodName) throws NoSuchMethodException {
        return Fixture.class.getDeclaredMethod(methodName).getAnnotation(SaMixCheckLogin.class);
    }

    private static AnnotatedElement element(String methodName) throws NoSuchMethodException {
        return Fixture.class.getDeclaredMethod(methodName);
    }

    @Test
    void getHandlerAnnotationClassIsSaMixCheckLogin() {
        assertEquals(SaMixCheckLogin.class, handler.getHandlerAnnotationClass());
    }

    @Test
    void checkWithoutTempTokenRequiresLogin() throws Exception {
        SaTokenContextMockUtil.setMockContext();
        assertThrows(SaTokenException.class, () -> handler.checkMethod(
                annotation("mixLoginOnly"), element("mixLoginOnly")));
    }

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

    @Test
    void getTokenPayloadExtractsTokenFields() {
        Map<String, Object> payload = handler.getTokenPayload(tempToken());

        assertEquals("sms", payload.get(AuthConstants.JWT_AUTH_TYPE));
        assertEquals("user-1", payload.get(AuthConstants.JWT_SUBJECT));
        assertNotNull(payload.get(AuthConstants.JWT_ISSUED_AT));
    }

    @Test
    void getTerminalPayloadExtractsDeviceFields() {
        Map<String, Object> payload = handler.getTerminalPayload(tempToken());

        assertEquals("PC", payload.get(AuthConstants.FIELD_DEVICE_TYPE));
        assertEquals("device-1", payload.get(AuthConstants.FIELD_DEVICE_ID));
        assertEquals("appstore", payload.get(AuthConstants.FIELD_APP_CHANNEL));
        assertEquals("1.2.3", payload.get(AuthConstants.FIELD_APP_VERSION));
    }

    @Test
    void checkIgnoresLoginFlagWhenTempTokenPresentButLoginDisabled() throws Exception {
        String tempToken = SaTempKit.createToken(tempToken(), 60);
        SaTokenContextMockUtil.setMockContext();
        SaRequestForMock request = (SaRequestForMock) SaHolder.getRequest();
        request.parameterMap.put(AuthConstants.PARAM_TEMP_TOKEN, tempToken);

        handler.checkMethod(annotation("mixLoginDisabled"), element("mixLoginDisabled"));

        assertFalse(StpUtil.isLogin());
    }

    @SuppressWarnings("unused")
    private static class Fixture {

        @SaMixCheckLogin
        public void mixLoginOnly() {
        }

        @SaMixCheckLogin(login = true, throwaway = true)
        public void mixLoginThrowaway() {
        }

        @SaMixCheckLogin(login = true, throwaway = false)
        public void mixLoginPersistent() {
        }

        @SaMixCheckLogin(login = false, throwaway = false)
        public void mixLoginDisabled() {
        }
    }
}
