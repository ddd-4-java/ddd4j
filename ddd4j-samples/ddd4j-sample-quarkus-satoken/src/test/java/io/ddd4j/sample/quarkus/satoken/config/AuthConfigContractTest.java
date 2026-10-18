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

/** 权限数据提供者在三线上的共同契约。 */
class AuthConfigContractTest {

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

    private static void assertReadOnly(List<String> expected, List<String> actual) {
        assertEquals(expected, actual);
        assertThrows(UnsupportedOperationException.class, () -> actual.set(0, "changed"));
    }
}
