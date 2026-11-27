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
package io.ddd4j.core.constant;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u9501\u5b9a\u8ba4\u8bc1\u5b57\u6bb5\u53ca JWT \u58f0\u660e\u5bf9\u8c03\u7528\u65b9\u516c\u5f00\u7684\u5b57\u7b26\u4e32\u3002 */
class AuthConstantsTest {

    /**
     * \u6838\u5bf9<code>field.getType()</code> \u7b49\u4e8e <code>String.class</code>\uff0c<code>field.getModifiers()</code> \u7b49\u4e8e <code>Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL</code>\u3002
     * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
     * @param literal \u4f20\u7ed9 <code>assertEquals()</code> \u7684 <code>String</code> \u8f93\u5165
     * @throws ReflectiveOperationException \u65e0\u6cd5\u53cd\u5c04\u8bbf\u95ee\u6d4b\u8bd5\u6240\u9700\u7684\u6784\u4ef6
     */
    private static void assertKey(String name, String literal) throws ReflectiveOperationException {
        Field field = AuthConstants.class.getField(name);
        assertEquals(String.class, field.getType(), name);
        assertEquals(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL, field.getModifiers(), name);
        assertEquals(literal, field.get(null), name);
    }

    /**
     * \u9a8c\u8bc1\u8ba4\u8bc1\u4e3b\u4f53\u516c\u5f00\u5b57\u6bb5\u540d\u4fdd\u6301\u517c\u5bb9\u3002
     * @throws ReflectiveOperationException \u65e0\u6cd5\u53cd\u5c04\u8bfb\u53d6\u6d4b\u8bd5\u8981\u6c42\u9a8c\u8bc1\u7684\u516c\u5f00\u5b57\u6bb5
     */
    @Test
    void publishedFieldKeysKeepTheirLiteralValues() throws ReflectiveOperationException {
        assertKey("FIELD_USER_ID", "uid");
        assertKey("FIELD_USER_NAME", "uname");
        assertKey("FIELD_ROLE_ID", "rid");
        assertKey("FIELD_ORG_ID", "org_id");
        assertKey("FIELD_PARENT_ID", "pid");
        assertKey("FIELD_ACCOUNT", "acc");
        assertKey("FIELD_TENANT_ID", "tenant_id");
        assertKey("FIELD_ROLE_CODE", "roleCode");
        assertKey("FIELD_ROLE_TYPE", "roleType");
    }

    /**
     * \u9a8c\u8bc1\u8ba4\u8bc1\u6a21\u5757\u516c\u5f00\u7684 JWT \u58f0\u660e\u952e\u4fdd\u6301\u89c4\u5b9a\u62fc\u5199\u3002
     * @throws ReflectiveOperationException \u65e0\u6cd5\u53cd\u5c04\u8bfb\u53d6\u6d4b\u8bd5\u8981\u6c42\u9a8c\u8bc1\u7684\u516c\u5f00\u5b57\u6bb5
     */
    @Test
    void claimKeysExposeThePublishedJwtNames() throws ReflectiveOperationException {
        assertKey("CLAIM_TENANT_ID", "tenant_id");
        assertKey("CLAIM_USER_ID", "uid");
        assertKey("CLAIM_ROLE_ID", "rid");
        assertKey("CLAIM_ORG_ID", "org_id");
    }
}
