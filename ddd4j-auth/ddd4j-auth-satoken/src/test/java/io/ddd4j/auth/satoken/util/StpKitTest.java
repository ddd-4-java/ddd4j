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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class StpKitTest {

    private SaTokenConfig originalConfig;
    private SaTokenDao originalDao;
    private StpLogic originalLogic;

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

    @AfterEach
    void tearDown() {
        cn.dev33.satoken.context.mock.SaTokenContextMockUtil.clearContext();
        StpUtil.setStpLogic(originalLogic);
        SaManager.setSaTokenDao(originalDao);
        SaManager.setConfig(originalConfig);
    }

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

    @Test
    void loginIdAccessorsReturnTypedValues() {
        loginWithExtras();

        assertEquals(Long.valueOf(1001), StpKit.getLoginIdAsLong());
        assertEquals("1001", StpKit.getLoginIdAsString());
        assertEquals(Integer.valueOf(1001), StpKit.getLoginIdAsInteger());
    }

    @Test
    void userIdAccessorsReadExtraData() {
        loginWithExtras();

        assertEquals(1001L, ((Number) StpKit.getUserId()).longValue());
        assertEquals(1001L, StpKit.getUserIdAsLong().longValue());
        assertEquals("1001", StpKit.getUserIdAsString());
        assertEquals(Integer.valueOf(1001), StpKit.getUserIdAsInteger());
    }

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

    @Test
    void instanceExtraAccessorsDelegate() {
        loginWithExtras();
        String token = StpUtil.getTokenValue();
        StpKit kit = new StpKit();

        assertEquals("custom-value", kit.getExtraAsString(token, "custom-key"));
        assertEquals(Integer.valueOf(1001), kit.getExtraAsInteger(token, AuthConstants.FIELD_USER_ID));
    }

    @Test
    void defaultAndNamedStpLogicsAreAvailable() {
        assertNotNull(StpKit.DEFAULT);
        assertNotNull(StpKit.ADMIN);
        assertNotNull(StpKit.USER);
        assertNotNull(StpKit.XXX);
    }
}
