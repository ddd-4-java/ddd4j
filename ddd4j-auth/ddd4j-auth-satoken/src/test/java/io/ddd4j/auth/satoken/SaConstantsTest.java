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

/** \u901a\u8fc7\u53cd\u5c04\u9501\u5b9a Sa-Token \u96c6\u6210\u516c\u5f00\u7684\u4ee4\u724c\u8d1f\u8f7d\u5b57\u6bb5\u540d\u79f0\u3002 */
class SaConstantsTest {

    /**
     * \u6838\u5bf9<code>field.getType()</code> \u7b49\u4e8e <code>String.class</code>\uff0c<code>field.getModifiers()</code> \u7b49\u4e8e <code>Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL</code>\u3002
     * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
     * @param literal \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>String</code> \u8f93\u5165
     * @throws ReflectiveOperationException \u65e0\u6cd5\u53cd\u5c04\u8bbf\u95ee\u6d4b\u8bd5\u6240\u9700\u7684\u6784\u4ef6
     */
    private static void assertKey(String name, String literal) throws ReflectiveOperationException {
        Field field = SaConstants.class.getField(name);
        assertEquals(String.class, field.getType(), name);
        assertEquals(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL, field.getModifiers(), name);
        assertEquals(literal, field.get(null), name);
    }

    /**
     * \u9a8c\u8bc1\u4ee4\u724c\u8d1f\u8f7d\u7684\u901a\u7528\u79df\u6237\u4e0e\u5e94\u7528\u6807\u8bc6\u952e\u4fdd\u6301\u65e2\u6709\u5b57\u7b26\u4e32\u3002
     * @throws ReflectiveOperationException \u65e0\u6cd5\u53cd\u5c04\u8bfb\u53d6\u6d4b\u8bd5\u8981\u6c42\u9a8c\u8bc1\u7684\u516c\u5f00\u5b57\u6bb5
     */
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

    /**
     * \u9a8c\u8bc1\u4ee4\u724c\u8d1f\u8f7d\u7684\u5b66\u6821\u76f8\u5173\u5b57\u6bb5\u540d\u4fdd\u6301\u65e2\u6709\u5b57\u7b26\u4e32\u3002
     * @throws ReflectiveOperationException \u65e0\u6cd5\u53cd\u5c04\u8bfb\u53d6\u6d4b\u8bd5\u8981\u6c42\u9a8c\u8bc1\u7684\u516c\u5f00\u5b57\u6bb5
     */
    @Test
    void schoolPayloadKeysKeepTheirLiteralValues() throws ReflectiveOperationException {
        assertKey("PAYLOAD_SCHOOL_CODE", "xxdm");
        assertKey("PAYLOAD_XQ_ORG_ID", "xq_org_id");
        assertKey("PAYLOAD_IDENTITY_ID", "iden_id");
        assertKey("PAYLOAD_INFO_ID", "info_id");
        assertKey("PAYLOAD_PARENT_INFO_ID", "p_info_id");
    }
}
