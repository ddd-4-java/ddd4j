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
import cn.dev33.satoken.jwt.SaJwtUtil;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import io.ddd4j.auth.satoken.SaConstants;
import io.ddd4j.auth.satoken.config.SaTokenAuthenticationMode;
import io.ddd4j.auth.satoken.config.SaTokenSecurityConfigurer;
import io.ddd4j.auth.satoken.config.SaTokenSecurityProperties;
import io.ddd4j.auth.satoken.subject.SaTokenSubject;
import io.ddd4j.core.auth.AuthRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
        extras.put("userId", 1001L);
        extras.put("orgId", 10L);
        extras.put("roleId", 7L);
        extras.put(SaConstants.PAYLOAD_TENANT_ID, "tenant-1");
        extras.put("appId", "app-1");
        extras.put("roleCode", "operator");
        extras.put("roleType", "custom");
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
    void orgAndRoleAccessorsReadTheirOwnClaims() {
        loginWithExtras();

        assertEquals(10L, ((Number) StpKit.getOrgId()).longValue());
        assertEquals("10", StpKit.getOrgIdAsString());
        assertEquals(Integer.valueOf(10), StpKit.getOrgIdAsInteger());
        assertEquals(10L, StpKit.getOrgIdAsLong().longValue());
        assertEquals(7L, ((Number) StpKit.getRoleId()).longValue());
        assertEquals("7", StpKit.getRoleIdAsString());
        assertEquals(Integer.valueOf(7), StpKit.getRoleIdAsInteger());
        assertEquals(7L, StpKit.getRoleIdAsLong().longValue());
    }

    @ParameterizedTest
    @CsvSource({"tenant_id,tenantId", "tenantId,tenant_id"})
    void signedJwtPreservesSuppliedTenantKey(String suppliedKey, String absentKey) {
        String token = new SaTokenSubject().login(AuthRequest.of("tenant-login").extra(suppliedKey, "tenant-1"));
        StpLogic logic = StpUtil.stpLogic;
        Map<String, Object> claims = SaJwtUtil.getPayloadsNotCheck(token, logic.getLoginType(), SaManager.getConfig().getJwtSecretKey());

        assertEquals("tenant-1", claims.get(suppliedKey));
        assertFalse(claims.containsKey(absentKey));
        assertEquals("tenant-1", StpKit.getExtraAs(token, suppliedKey, String.class));
    }

    @Test
    void commonPayloadConstantsResolveClaimsWithoutChangingExistingKeys() {
        loginWithExtras();

        assertEquals("tenant-1", StpKit.getExtraAsString("tenant_id"));
        assertEquals("tenant_id", SaConstants.PAYLOAD_TENANT_ID);
        assertEquals("tenant-1", StpKit.getExtraAsString(SaConstants.PAYLOAD_TENANT_ID));
        assertEquals("app-1", StpKit.getExtraAsString(SaConstants.PAYLOAD_APP_ID));
        assertEquals("operator", StpKit.getExtraAsString(SaConstants.PAYLOAD_ROLE_CODE));
        assertEquals("custom", StpKit.getExtraAsString(SaConstants.PAYLOAD_ROLE_TYPE));
        assertEquals("uid", SaConstants.PAYLOAD_USER_ID);
        assertEquals("rid", SaConstants.PAYLOAD_ROLE_ID);
        assertEquals("org_id", SaConstants.PAYLOAD_ORG_ID);
    }

    @Test
    void typedIdAccessorsPreferDocumentedClaimsWhenBothKeyStylesExist() {
        new SaTokenSubject().login(AuthRequest.of("mixed-keys")
                .extra("uid", 11).extra("userId", 12)
                .extra("org_id", 21).extra("orgId", 22)
                .extra("rid", 31).extra("roleId", 32));

        assertEquals("11", StpKit.getUserIdAsString());
        assertEquals(21L, StpKit.getOrgIdAsLong().longValue());
        assertEquals(31, StpKit.getRoleIdAsInteger().intValue());
    }

    @Test
    void campusSchoolAndIdentityAccessorsReadDocumentedClaims() {
        new SaTokenSubject().login(AuthRequest.of("campus-login")
                .extra("xq_org_id", "042")
                .extra("xxdm", "00123")
                .extra("iden_id", "7")
                .extra("org_id", 81));

        assertAll(
                () -> assertEquals("042", StpKit.class.getMethod("getXqOrgIdAsString").invoke(null)),
                () -> assertEquals(Integer.valueOf(42), StpKit.class.getMethod("getXqOrgIdAsInteger").invoke(null)),
                () -> assertEquals(Long.valueOf(42), StpKit.class.getMethod("getXqOrgIdAsLong").invoke(null)),
                () -> assertEquals("00123", StpKit.class.getMethod("getXxdmAsString").invoke(null)),
                () -> assertEquals(Integer.valueOf(7), StpKit.class.getMethod("getIdentityIdAsInteger").invoke(null)));
    }

    @Test
    void campusSchoolAndIdentityAccessorsReturnNullForMissingClaims() {
        loginWithExtras();

        assertAll(
                () -> assertNull(StpKit.class.getMethod("getXqOrgIdAsString").invoke(null)),
                () -> assertNull(StpKit.class.getMethod("getXqOrgIdAsInteger").invoke(null)),
                () -> assertNull(StpKit.class.getMethod("getXqOrgIdAsLong").invoke(null)),
                () -> assertNull(StpKit.class.getMethod("getXxdmAsString").invoke(null)),
                () -> assertNull(StpKit.class.getMethod("getIdentityIdAsInteger").invoke(null)));
    }

    @Test
    void infoIdStringAndIntegerReadOnlyTheInfoClaim() {
        String token = new SaTokenSubject().login(AuthRequest.of("info-login")
                .extra("org_id", "81").extra("rid", "7")
                .extra("infoId", "99").extra("info_id", "042"));
        Map<String, Object> claims = SaJwtUtil.getPayloadsNotCheck(token,
                StpUtil.stpLogic.getLoginType(), SaManager.getConfig().getJwtSecretKey());

        assertEquals("042", claims.get("info_id"));
        assertAll(
                () -> assertEquals("042", StpKit.class.getMethod("getInfoIdAsString").invoke(null)),
                () -> assertEquals(Integer.valueOf(42), StpKit.class.getMethod("getInfoIdAsInteger").invoke(null)));
    }

    @Test
    void infoIdStringAndIntegerReturnNullWithoutTheInfoClaim() {
        String token = new SaTokenSubject().login(AuthRequest.of("missing-info-login")
                .extra("org_id", "81").extra("rid", "7").extra("infoId", "99"));
        Map<String, Object> claims = SaJwtUtil.getPayloadsNotCheck(token,
                StpUtil.stpLogic.getLoginType(), SaManager.getConfig().getJwtSecretKey());

        assertFalse(claims.containsKey("info_id"));
        assertAll(
                () -> assertNull(StpKit.class.getMethod("getInfoIdAsString").invoke(null)),
                () -> assertNull(StpKit.class.getMethod("getInfoIdAsInteger").invoke(null)));
    }

    @Test
    void infoIdLongReadsHistoricalOrganizationClaimFromSignedToken() throws ReflectiveOperationException {
        String token = new SaTokenSubject().login(AuthRequest.of("legacy-info-long")
                .extra("org_id", "081").extra("orgId", "82").extra("info_id", "042"));
        Map<String, Object> claims = SaJwtUtil.getPayloadsNotCheck(token,
                StpUtil.stpLogic.getLoginType(), SaManager.getConfig().getJwtSecretKey());

        assertEquals("081", claims.get("org_id"));
        assertEquals("042", claims.get("info_id"));
        var accessor = assertDoesNotThrow(() -> StpKit.class.getMethod("getInfoIdAsLong"));
        assertEquals(Long.valueOf(81), accessor.invoke(null));
    }

    @Test
    void infoIdLongReturnsNullWithoutHistoricalOrganizationClaim() throws ReflectiveOperationException {
        String token = new SaTokenSubject().login(AuthRequest.of("legacy-info-long-missing")
                .extra("orgId", "82").extra("info_id", "042"));
        Map<String, Object> claims = SaJwtUtil.getPayloadsNotCheck(token,
                StpUtil.stpLogic.getLoginType(), SaManager.getConfig().getJwtSecretKey());

        assertFalse(claims.containsKey("org_id"));
        var accessor = assertDoesNotThrow(() -> StpKit.class.getMethod("getInfoIdAsLong"));
        assertNull(accessor.invoke(null));
    }

    @Test
    void infoIdLongPreservesInvalidOrganizationClaimError() {
        new SaTokenSubject().login(AuthRequest.of("legacy-info-long-invalid")
                .extra("org_id", "invalid").extra("orgId", "82").extra("info_id", "042"));

        var accessor = assertDoesNotThrow(() -> StpKit.class.getMethod("getInfoIdAsLong"));
        var error = assertThrows(InvocationTargetException.class, () -> accessor.invoke(null));
        assertInstanceOf(NumberFormatException.class, error.getCause());
    }

    @Test
    void getExtraAsAppliesMapperAndTypeConversion() {
        loginWithExtras();

        assertEquals("custom-value", StpKit.getExtraAsString("custom-key"));
        assertEquals(10L, StpKit.getExtraAsLong("orgId").longValue());
        assertNull(StpKit.getExtraAs("org_id", Long.class));
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
        assertEquals(10L, StpKit.getExtraAsLong(token, "orgId").longValue());
        assertEquals("custom-value", StpKit.getExtraAs(token, "custom-key", String.class));
        assertNull(StpKit.getExtraAs(token, "missing-key", String.class));
    }

    @Test
    void instanceExtraAccessorsDelegate() {
        loginWithExtras();
        String token = StpUtil.getTokenValue();
        StpKit kit = new StpKit();

        assertEquals("custom-value", kit.getExtraAsString(token, "custom-key"));
        assertEquals(Integer.valueOf(1001), kit.getExtraAsInteger(token, "userId"));
    }

    @Test
    void defaultAndNamedStpLogicsAreAvailable() {
        assertNotNull(StpKit.DEFAULT);
        assertNotNull(StpKit.ADMIN);
        assertNotNull(StpKit.USER);
        assertEquals("xx", StpKit.XXX.getLoginType());
        assertSame(StpKit.XXX, SaManager.getStpLogic("xx", false));
    }
}
