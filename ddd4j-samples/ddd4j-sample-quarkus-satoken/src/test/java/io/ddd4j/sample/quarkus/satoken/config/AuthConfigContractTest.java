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
package io.ddd4j.sample.quarkus.satoken.config;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.subject.SubjectDataProvider;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 权限数据提供者在三线上的共同契约。
 */
class AuthConfigContractTest {

    /**
     * \u6838\u5bf9<code>actual</code> \u7b49\u4e8e <code>expected</code>\uff0c<code>actual.set(0, "changed")</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002
     * @param expected \u5f53\u524d\u7528\u4f8b\u8bbe\u5b9a\u7684\u671f\u671b\u503c
     * @param actual \u53d7\u6d4b\u8c03\u7528\u6216\u6d4b\u8bd5\u73af\u5883\u5b9e\u9645\u4ea7\u751f\u7684\u503c
     */
    private static void assertReadOnly(List<String> expected, List<String> actual) {
        assertEquals(expected, actual);
        assertThrows(UnsupportedOperationException.class, () -> actual.set(0, "changed"));
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>Arrays.asList()</code>\u3001<code>provider.getPermissionList()</code>\u3001<code>provider.getRoleList()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void permissionAndRoleSnapshotsCannotBeChangedByCallers() {
        SubjectDataProvider provider = new AuthConfig().subjectDataProvider();
        AuthPrincipal admin = new AuthPrincipal().setLoginId("10001");
        AuthPrincipal user = new AuthPrincipal().setLoginId("10002");

        assertReadOnly(Arrays.asList("user:add", "user:delete", "user:list"),
                provider.getPermissionList(admin));
        assertReadOnly(Arrays.asList("admin", "user"), provider.getRoleList(admin));
        assertReadOnly(Arrays.asList("user:list"), provider.getPermissionList(user));
        assertReadOnly(Arrays.asList("user"), provider.getRoleList(user));
    }
}
