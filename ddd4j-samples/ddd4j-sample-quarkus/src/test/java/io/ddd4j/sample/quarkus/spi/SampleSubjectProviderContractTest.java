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
package io.ddd4j.sample.quarkus.spi;

import io.ddd4j.core.auth.AuthPrincipal;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 三线共同的示例认证主体权限契约。 */
class SampleSubjectProviderContractTest {

    @Test
    void createsMutablePrincipalPermissions() {
        SampleSubjectProvider provider = new SampleSubjectProvider();
        AuthPrincipal principal = provider.getSubject().getPrincipalByToken(provider.token());

        assertNotNull(principal);
        assertEquals(new HashSet<>(Arrays.asList("order:read", "order:write")), principal.getPerms());
        principal.getPerms().add("order:refund");
        assertTrue(principal.getPerms().contains("order:refund"));
    }
}
