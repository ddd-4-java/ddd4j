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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Sa-Token 权限和角色数据桥接契约。
 */
class SaTokenSubjectDataBridgeTest {

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
}
