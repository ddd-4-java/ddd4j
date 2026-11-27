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

/**
 * 通过反射锁定 Sa-Token 集成公开的令牌负载字段名称。
 */
class SaConstantsTest {

    /**
     * 核对<code>field.getType()</code> 等于 <code>String.class</code>，<code>field.getModifiers()</code> 等于 <code>Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL</code>。
     *
     * @param name    当前测试指定的对象或属性名称
     * @param literal 传给 <code>assertEquals()</code> 的 <code>String</code> 输入
     * @throws ReflectiveOperationException 无法反射访问测试所需的构件
     */
    private static void assertKey(String name, String literal) throws ReflectiveOperationException {
        Field field = SaConstants.class.getField(name);
        assertEquals(String.class, field.getType(), name);
        assertEquals(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL, field.getModifiers(), name);
        assertEquals(literal, field.get(null), name);
    }

    /**
     * 验证令牌负载的通用租户与应用标识键保持既有字符串。
     *
     * @throws ReflectiveOperationException 无法反射读取测试要求验证的公开字段
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
     * 验证令牌负载的学校相关字段名保持既有字符串。
     *
     * @throws ReflectiveOperationException 无法反射读取测试要求验证的公开字段
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
