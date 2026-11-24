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
package io.ddd4j.auth.satoken;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaConstantsTest {

    @Test
    void generalPayloadKeysKeepTheirLiteralValues() throws ReflectiveOperationException {
        assertKey("PAYLOAD_TENANT_ID", "tenant_id");
        assertKey("PAYLOAD_APP_ID", "appId");
        assertKey("PAYLOAD_ROLE_ID", "rid");
        assertKey("PAYLOAD_ROLE_CODE", "roleCode");
        assertKey("PAYLOAD_ROLE_TYPE", "roleType");
        assertKey("PAYLOAD_USER_ID", "uid");
        assertKey("PAYLOAD_ORG_ID", "org_id");
    }

    @Test
    void schoolPayloadKeysKeepTheirLiteralValues() throws ReflectiveOperationException {
        assertKey("PAYLOAD_SCHOOL_CODE", "xxdm");
        assertKey("PAYLOAD_XQ_ORG_ID", "xq_org_id");
        assertKey("PAYLOAD_IDENTITY_ID", "iden_id");
        assertKey("PAYLOAD_INFO_ID", "info_id");
        assertKey("PAYLOAD_PARENT_INFO_ID", "p_info_id");
    }

    private static void assertKey(String name, String literal) throws ReflectiveOperationException {
        Field field = SaConstants.class.getField(name);
        assertEquals(String.class, field.getType(), name);
        assertEquals(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL, field.getModifiers(), name);
        assertEquals(literal, field.get(null), name);
    }
}
