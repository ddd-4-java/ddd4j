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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 Sa-Token 权限和角色数据桥接契约。
 */
class SaTokenSubjectDataBridgeTest {

    /**
     * 构造本组测试输入时复用的固定标识或名称。
     */
    private static final String SHARED_LOGIN_ID = "data-bridge-shared-account";
    /**
     * 用于隔离管理员身份的测试认证域。
     */
    private static final String ADMIN_REALM = "data-bridge-admin-realm";
    /**
     * 用于验证未知账号域处理的测试标识。
     */
    private static final String UNKNOWN_REALM = "data-bridge-unknown-realm";

    /**
     * 被验证是否传递上下文或适配请求的桥接对象。
     */
    private final SaTokenSubjectDataBridge bridge = new SaTokenSubjectDataBridge();
    /**
     * 用例修改前注册的服务提供器，供用例结束时恢复。
     */
    private SubjectProvider originalProvider;
    /**
     * 进入当前执行范围前保存的组件，退出时用于恢复 <code>SubjectDataProvider</code>。
     */
    private SubjectDataProvider originalDataProvider;

    /**
     * 测试前依次注册<code>SubjectKit</code>。
     */
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

    /**
     * 测试后依次注册<code>SubjectKit</code>。
     */
    @AfterEach
    void tearDown() {
        SubjectKit.register(originalProvider);
        SubjectKit.setDataProvider(originalDataProvider);
    }

    /**
     * 验证<code>permissions</code> 等于 <code>Arrays.asList("order:read", "order:write")</code>。
     */
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

    /**
     * 验证<code>permissions.isEmpty()</code>为真。
     */
    @Test
    void getPermissionListReturnsEmptyForUnknownAccount() {
        List<String> permissions = bridge.getPermissionList("ghost", "login");

        assertTrue(permissions.isEmpty());
    }

    /**
     * 验证<code>roles</code> 等于 <code>Collections.singletonList("admin")</code>。
     */
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

    /**
     * 验证<code>roles.isEmpty()</code>为真。
     */
    @Test
    void getRoleListReturnsEmptyForUnknownAccount() {
        List<String> roles = bridge.getRoleList("ghost", "login");

        assertTrue(roles.isEmpty());
    }

    /**
     * 验证<code>bridge.getPermissionList(SHARED_LOGIN_ID, defaultLogic.getLoginType())</code> 等于 <code>Collections.singletonList("customer:read")</code>，<code>bridge.getPermissionList(SHARED_LOGIN_ID, ADMIN_REALM)</code> 等于 <code>Collections.singletonList("operator:read")</code>。
     */
    @Test
    void getPermissionListUsesRequestedLoginType() {
        withRealmSessions(new SaTokenSubjectProvider(), defaultLogic -> assertAll(
                () -> assertEquals(Collections.singletonList("customer:read"),
                        bridge.getPermissionList(SHARED_LOGIN_ID, defaultLogic.getLoginType())),
                () -> assertEquals(Collections.singletonList("operator:read"),
                        bridge.getPermissionList(SHARED_LOGIN_ID, ADMIN_REALM))));
    }

    /**
     * 验证<code>bridge.getRoleList(SHARED_LOGIN_ID, defaultLogic.getLoginType())</code> 等于 <code>Collections.singletonList("customer")</code>，<code>bridge.getRoleList(SHARED_LOGIN_ID, ADMIN_REALM)</code> 等于 <code>Collections.singletonList("operator")</code>。
     */
    @Test
    void getRoleListUsesRequestedLoginType() {
        withRealmSessions(new SaTokenSubjectProvider(), defaultLogic -> assertAll(
                () -> assertEquals(Collections.singletonList("customer"),
                        bridge.getRoleList(SHARED_LOGIN_ID, defaultLogic.getLoginType())),
                () -> assertEquals(Collections.singletonList("operator"),
                        bridge.getRoleList(SHARED_LOGIN_ID, ADMIN_REALM))));
    }

    /**
     * 验证<code>bridge.getPermissionList("missing-account", ADMIN_REALM).isEmpty()</code>为真，<code>bridge.getRoleList("missing-account", ADMIN_REALM).isEmpty()</code>为真，<code>bridge.getRoleList(SHARED_LOGIN_ID, UNKNOWN_REALM).isEmpty()</code>为真。
     */
    @Test
    void missingAccountAndLoginTypeReturnEmptyLists() {
        withRealmSessions(new SaTokenSubjectProvider(), defaultLogic -> assertAll(
                () -> assertTrue(bridge.getPermissionList("missing-account", ADMIN_REALM).isEmpty()),
                () -> assertTrue(bridge.getRoleList("missing-account", ADMIN_REALM).isEmpty()),
                () -> assertTrue(bridge.getPermissionList(SHARED_LOGIN_ID, UNKNOWN_REALM).isEmpty()),
                () -> assertTrue(bridge.getRoleList(SHARED_LOGIN_ID, UNKNOWN_REALM).isEmpty())));
    }

    /**
     * 验证<code>bridge.getPermissionList(SHARED_LOGIN_ID, loginType)</code> 等于 <code>Collections.singletonList("customer:read")</code>，<code>bridge.getRoleList(SHARED_LOGIN_ID, loginType)</code> 等于 <code>Collections.singletonList("customer")</code>，<code>bridge.getRoleList("missing-account", loginType).isEmpty()</code>为真。
     */
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

    /**
     * 验证<code>bridge.getPermissionList(SHARED_LOGIN_ID, defaultLogic.getLoginType())</code> 等于 <code>Collections.singletonList("customer:read")</code>，<code>bridge.getPermissionList(SHARED_LOGIN_ID, ADMIN_REALM)</code> 等于 <code>Collections.singletonList("operator:read")</code>，<code>bridge.getRoleList(SHARED_LOGIN_ID, ADMIN_REALM)</code> 等于 <code>Collections.singletonList("operator")</code>。
     */
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
     *
     * @param provider 创建两个认证域主体的提供器
     * @param verify   在测试会话有效期内运行的断言
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

    /**
     * 调用 <code>logic.getSessionByLoginId()</code> 执行当前夹具操作。
     *
     * @param logic    传给 <code>logic.getSessionByLoginId()</code> 的 <code>StpLogic</code> 输入
     * @param identity 传给 <code>new AuthPrincipal</code> 的 <code>String</code> 输入
     */
    private void storeRealmPrincipal(StpLogic logic, String identity) {
        AuthPrincipal principal = new AuthPrincipal().setLoginId(SHARED_LOGIN_ID).setUserId(identity).setRoleId(identity);
        logic.getSessionByLoginId(SHARED_LOGIN_ID).set(SaTokenSubject.PRINCIPAL_KEY, principal);
    }
}
