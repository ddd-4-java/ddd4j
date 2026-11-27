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

/**
 * 锁定认证字段及 JWT 声明对调用方公开的字符串。
 */
class AuthConstantsTest {

    /**
     * 核对<code>field.getType()</code> 等于 <code>String.class</code>，<code>field.getModifiers()</code> 等于 <code>Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL</code>。
     *
     * @param name    当前测试指定的对象或属性名称
     * @param literal 传给 <code>assertEquals()</code> 的 <code>String</code> 输入
     * @throws ReflectiveOperationException 无法反射访问测试所需的构件
     */
    private static void assertKey(String name, String literal) throws ReflectiveOperationException {
        Field field = AuthConstants.class.getField(name);
        assertEquals(String.class, field.getType(), name);
        assertEquals(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL, field.getModifiers(), name);
        assertEquals(literal, field.get(null), name);
    }

    /**
     * 验证认证主体公开字段名保持兼容。
     *
     * @throws ReflectiveOperationException 无法反射读取测试要求验证的公开字段
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
     * 验证认证模块公开的 JWT 声明键保持规定拼写。
     *
     * @throws ReflectiveOperationException 无法反射读取测试要求验证的公开字段
     */
    @Test
    void claimKeysExposeThePublishedJwtNames() throws ReflectiveOperationException {
        assertKey("CLAIM_TENANT_ID", "tenant_id");
        assertKey("CLAIM_USER_ID", "uid");
        assertKey("CLAIM_ROLE_ID", "rid");
        assertKey("CLAIM_ORG_ID", "org_id");
    }
}
