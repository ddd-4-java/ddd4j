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
package io.ddd4j.web.core.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 认证结果的值语义与双访问器契约。 */
class AuthenticationValueContractTest {

    @Test
    void equalResultsHaveEqualHashAndReadableToken() {
        BearerSubjectAuthenticator.Authentication first =
                new BearerSubjectAuthenticator.Authentication("token", null, null);
        BearerSubjectAuthenticator.Authentication second =
                new BearerSubjectAuthenticator.Authentication("token", null, null);
        BearerSubjectAuthenticator.Authentication different =
                new BearerSubjectAuthenticator.Authentication("another", null, null);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, different);
        assertEquals("token", first.token());
        assertEquals("token", first.getToken());
        assertNull(first.principal());
        assertNull(first.getPrincipal());
        assertNull(first.subject());
        assertNull(first.getSubject());
    }
}
