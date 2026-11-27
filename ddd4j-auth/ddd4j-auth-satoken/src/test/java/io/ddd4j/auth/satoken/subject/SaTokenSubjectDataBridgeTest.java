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
import java.util.function.Consumer;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** \u88ab\u9a8c\u8bc1\u662f\u5426\u4f20\u9012\u4e0a\u4e0b\u6587\u6216\u9002\u914d\u8bf7\u6c42\u7684\u6865\u63a5\u5bf9\u8c61\u3002 */
    private final SaTokenSubjectDataBridge bridge = new SaTokenSubjectDataBridge();
    /** \u7528\u4f8b\u4fee\u6539\u524d\u6ce8\u518c\u7684\u670d\u52a1\u63d0\u4f9b\u5668\uff0c\u4f9b\u7528\u4f8b\u7ed3\u675f\u65f6\u6062\u590d\u3002 */
    private SubjectProvider originalProvider;
    /** \u8fdb\u5165\u5f53\u524d\u6267\u884c\u8303\u56f4\u524d\u4fdd\u5b58\u7684\u7ec4\u4ef6\uff0c\u9000\u51fa\u65f6\u7528\u4e8e\u6062\u590d <code>SubjectDataProvider</code>\u3002 */
    private SubjectDataProvider originalDataProvider;

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u6ce8\u518c<code>SubjectKit</code>\u3002 */
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

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6ce8\u518c<code>SubjectKit</code>\u3002 */
    @AfterEach
    void tearDown() {
        SubjectKit.register(originalProvider);
        SubjectKit.setDataProvider(originalDataProvider);
    }

    /** \u9a8c\u8bc1<code>permissions</code> \u7b49\u4e8e <code>Arrays.asList("order:read", "order:write")</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>permissions.isEmpty()</code>\u4e3a\u771f\u3002 */
    @Test
    void getPermissionListReturnsEmptyForUnknownAccount() {
        List<String> permissions = bridge.getPermissionList("ghost", "login");

        assertTrue(permissions.isEmpty());
    }

    /** \u9a8c\u8bc1<code>roles</code> \u7b49\u4e8e <code>Collections.singletonList("admin")</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>roles.isEmpty()</code>\u4e3a\u771f\u3002 */
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
