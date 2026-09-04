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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaTempKitTest {

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
    }

    @AfterEach
    void tearDown() {
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

    @Test
    void createAndParseTokenRoundTrips() {
        SaTempToken value = tempToken();
        String token = SaTempKit.createToken(value, 60);

        SaTempToken parsed = SaTempKit.parseToken(token);

        assertNotNull(parsed);
        assertEquals("user-1", parsed.getLoginId());
        assertEquals("sms", parsed.getAuthType());
    }

    @Test
    void parseTokenWithServiceRejectsPlainToken() {
        String token = SaTempKit.createToken(tempToken(), 60);

        assertThrows(SaTokenException.class, () -> SaTempKit.parseToken("custom-service", token));
    }

    @Test
    void getTimeoutReflectsRemainingLifetime() {
        String token = SaTempKit.createToken(tempToken(), 60);

        long timeout = SaTempKit.getTimeout(token);

        assertTrue(timeout <= 60 && timeout > 0);
    }

    @Test
    void deleteTokenInvalidatesIt() {
        String token = SaTempKit.createToken(tempToken(), 60);
        SaTempKit.deleteToken(token);

        assertEquals(-2, SaTempKit.getTimeout(token));
    }

    @Test
    void checkTempTokenValidatesStringToken() {
        String token = SaTempKit.createToken(tempToken(), 60);

        SaTempToken checked = SaTempKit.checkTempToken(token);

        assertEquals("user-1", checked.getLoginId());
    }

    @Test
    void checkTempTokenRejectsBlankToken() {
        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken((String) null));
        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken("  "));
    }

    @Test
    void checkTempTokenRejectsExpiredToken() {
        String token = SaTempKit.createToken(tempToken(), 60);
        SaTempKit.deleteToken(token);

        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken(token));
    }

    @Test
    void checkTempTokenRejectsMissingLoginId() {
        SaTempToken noLoginId = new SaTempToken();
        noLoginId.setDeviceType("PC");
        String token = SaTempKit.createToken(noLoginId, 60);

        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken(token));
    }

    @Test
    void checkTempTokenValidatesParsedObject() {
        SaTempToken value = tempToken();

        assertSame(value, SaTempKit.checkTempToken(value));
    }

    @Test
    void checkTempTokenRejectsNullParsedObject() {
        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken((SaTempToken) null));
    }

    @Test
    void checkTempTokenRejectsParsedObjectWithoutLoginId() {
        SaTempToken noLoginId = new SaTempToken();
        noLoginId.setDeviceId("device-1");

        assertThrows(SaTokenException.class, () -> SaTempKit.checkTempToken(noLoginId));
    }
}
