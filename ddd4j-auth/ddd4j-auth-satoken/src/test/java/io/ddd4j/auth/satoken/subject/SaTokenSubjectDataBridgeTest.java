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
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Sa-Token 权限和角色数据桥接契约。
 */
class SaTokenSubjectDataBridgeTest {

    private static final String SHARED_LOGIN_ID = "data-bridge-shared-account";
    private static final String ADMIN_REALM = "data-bridge-admin-realm";
    private static final String UNKNOWN_REALM = "data-bridge-unknown-realm";

    private final SaTokenSubjectDataBridge bridge = new SaTokenSubjectDataBridge();
    private SubjectProvider originalProvider;
    private SubjectDataProvider originalDataProvider;

    @BeforeEach
    void setUp() {
        originalProvider = SubjectKit.subjectProvider;
        originalDataProvider = SubjectKit.dataProvider;
        SubjectKit.register(new SubjectProvider() {
            /**
             * 返回当前测试使用的 Subject。
             *
             * @return 测试 Subject
             */
            @Override
            public Subject getSubject() {
                return new SaTokenSubject() {
                    /**
                     * 按登录标识返回测试用户。
                     *
                     * @param <T> 用户类型
                     * @param loginId 登录标识。
                     * @return 匹配的用户，未匹配时返回 null
                     */
                    @Override
                    public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
                        if ("user-1".equals(loginId)) {
                            return (T) new AuthPrincipal().setLoginId("user-1").setUserId("u-1");
                        }
                        return null;
                    }
                };
            }
        });
    }

    @AfterEach
    void tearDown() {
        SubjectKit.register(originalProvider);
        SubjectKit.setDataProvider(originalDataProvider);
    }

    @Test
    void getPermissionListDelegatesToDataProvider() {
        SubjectKit.setDataProvider(new SubjectDataProvider() {
            @Override
            public List<String> getPermissionList(AuthPrincipal principal) {
                return Arrays.asList("order:read", "order:write");
            }
        });

        List<String> permissions = bridge.getPermissionList("user-1", "login");

        assertEquals(Arrays.asList("order:read", "order:write"), permissions);
    }

    @Test
    void getPermissionListReturnsEmptyForUnknownAccount() {
        List<String> permissions = bridge.getPermissionList("ghost", "login");

        assertTrue(permissions.isEmpty());
    }

    @Test
    void getRoleListDelegatesToDataProvider() {
        SubjectKit.setDataProvider(new SubjectDataProvider() {
            @Override
            public List<String> getRoleList(AuthPrincipal principal) {
                return Collections.singletonList("admin");
            }
        });

        List<String> roles = bridge.getRoleList("user-1", "login");

        assertEquals(Collections.singletonList("admin"), roles);
    }

    @Test
    void getRoleListReturnsEmptyForUnknownAccount() {
        List<String> roles = bridge.getRoleList("ghost", "login");

        assertTrue(roles.isEmpty());
    }

    @Test
    void getPermissionListUsesRequestedLoginType() {
        withRealmSessions(new SaTokenSubjectProvider(), defaultLogic -> assertAll(
                () -> assertEquals(Collections.singletonList("customer:read"),
                        bridge.getPermissionList(SHARED_LOGIN_ID, defaultLogic.getLoginType())),
                () -> assertEquals(Collections.singletonList("operator:read"),
                        bridge.getPermissionList(SHARED_LOGIN_ID, ADMIN_REALM))));
    }

    @Test
    void getRoleListUsesRequestedLoginType() {
        withRealmSessions(new SaTokenSubjectProvider(), defaultLogic -> assertAll(
                () -> assertEquals(Collections.singletonList("customer"),
                        bridge.getRoleList(SHARED_LOGIN_ID, defaultLogic.getLoginType())),
                () -> assertEquals(Collections.singletonList("operator"),
                        bridge.getRoleList(SHARED_LOGIN_ID, ADMIN_REALM))));
    }

    @Test
    void missingAccountAndLoginTypeReturnEmptyLists() {
        withRealmSessions(new SaTokenSubjectProvider(), defaultLogic -> assertAll(
                () -> assertTrue(bridge.getPermissionList("missing-account", ADMIN_REALM).isEmpty()),
                () -> assertTrue(bridge.getRoleList("missing-account", ADMIN_REALM).isEmpty()),
                () -> assertTrue(bridge.getPermissionList(SHARED_LOGIN_ID, UNKNOWN_REALM).isEmpty()),
                () -> assertTrue(bridge.getRoleList(SHARED_LOGIN_ID, UNKNOWN_REALM).isEmpty())));
    }

    @Test
    void blankLoginTypesUseDefaultAccountSessions() {
        withRealmSessions(new SaTokenSubjectProvider(), defaultLogic -> {
            for (String loginType : Arrays.asList(null, "", " ")) {
                assertEquals(Collections.singletonList("customer:read"),
                        bridge.getPermissionList(SHARED_LOGIN_ID, loginType));
                assertEquals(Collections.singletonList("customer"), bridge.getRoleList(SHARED_LOGIN_ID, loginType));
                assertTrue(bridge.getPermissionList("missing-account", loginType).isEmpty());
                assertTrue(bridge.getRoleList("missing-account", loginType).isEmpty());
            }
        });
    }

    @Test
    void dataSubjectProviderUsesRequestedLoginType() {
        withRealmSessions(new SaTokenDataSubjectProvider(), defaultLogic -> assertAll(
                () -> assertEquals(Collections.singletonList("customer:read"),
                        bridge.getPermissionList(SHARED_LOGIN_ID, defaultLogic.getLoginType())),
                () -> assertEquals(Collections.singletonList("operator:read"),
                        bridge.getPermissionList(SHARED_LOGIN_ID, ADMIN_REALM)),
                () -> assertEquals(Collections.singletonList("customer"),
                        bridge.getRoleList(SHARED_LOGIN_ID, defaultLogic.getLoginType())),
                () -> assertEquals(Collections.singletonList("operator"),
                        bridge.getRoleList(SHARED_LOGIN_ID, ADMIN_REALM))));
    }

    /**
     * 使用独立内存仓储为默认和管理账号体系写入同一登录 ID 的不同主体。
     */
    private void withRealmSessions(SubjectProvider provider, Consumer<StpLogic> verify) {
        SaTokenDao originalDao = SaManager.getSaTokenDao();
        try {
            SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
            StpLogic defaultLogic = StpUtil.stpLogic;
            storeRealmPrincipal(defaultLogic, "customer");
            storeRealmPrincipal(SaManager.getStpLogic(ADMIN_REALM), "operator");
            SubjectKit.register(provider);
            SubjectKit.setDataProvider(new SubjectDataProvider() {
                @Override
                public List<String> getPermissionList(AuthPrincipal principal) {
                    return Collections.singletonList(principal.getUserId() + ":read");
                }

                @Override
                public List<String> getRoleList(AuthPrincipal principal) {
                    return Collections.singletonList(String.valueOf(principal.getRoleId()));
                }
            });
            verify.accept(defaultLogic);
        } finally {
            SaManager.removeStpLogic(ADMIN_REALM);
            SaManager.removeStpLogic(UNKNOWN_REALM);
            SaManager.setSaTokenDao(originalDao);
        }
    }

    private void storeRealmPrincipal(StpLogic logic, String identity) {
        AuthPrincipal principal = new AuthPrincipal().setLoginId(SHARED_LOGIN_ID).setUserId(identity).setRoleId(identity);
        logic.getSessionByLoginId(SHARED_LOGIN_ID).set(SaTokenSubject.PRINCIPAL_KEY, principal);
    }
}
