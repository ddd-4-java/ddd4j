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
package io.ddd4j.data.jpa.crypto;

import io.ddd4j.data.crypto.annotation.EncryptField;
import io.ddd4j.data.crypto.handler.Ddd4jFieldCryptoHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 三线 JPA 监听器对标注字段和普通字段的加解密契约。 */
class JpaEncryptFieldListenerContractTest {

    /** \u9a8c\u8bc1<code>entity.secret</code> \u7b49\u4e8e <code>"cipher"</code>\uff0c<code>entity.plain</code> \u7b49\u4e8e <code>"plain"</code>\uff0c<code>entity.secret</code> \u7b49\u4e8e <code>"secret"</code>\u3002 */
    @Test
    void lifecycleEncryptsAndDecryptsInheritedFieldOnly() {
        Ddd4jFieldCryptoHandler handler = mock(Ddd4jFieldCryptoHandler.class);
        when(handler.encrypt("secret")).thenReturn("cipher");
        when(handler.decrypt("cipher")).thenReturn("secret");
        JpaEncryptFieldListener listener = new JpaEncryptFieldListener(handler);
        TestEntity entity = new TestEntity();
        entity.secret = "secret";
        entity.plain = "plain";

        listener.prePersist(entity);
        assertEquals("cipher", entity.secret);
        assertEquals("plain", entity.plain);

        listener.postLoad(entity);
        assertEquals("secret", entity.secret);
        assertEquals("plain", entity.plain);

        listener.preUpdate(entity);
        assertEquals("cipher", entity.secret);
    }

    /** \u9a8c\u8bc1<code>entity.secret</code>\u4e3a\u7a7a\uff0c<code>entity.secret</code> \u7b49\u4e8e <code>""</code>\u3002 */
    @Test
    void emptyAndNullFieldsStayUnchanged() {
        JpaEncryptFieldListener listener = new JpaEncryptFieldListener(mock(Ddd4jFieldCryptoHandler.class));
        TestEntity entity = new TestEntity();

        listener.prePersist(entity);
        assertNull(entity.secret);

        entity.secret = "";
        listener.prePersist(entity);
        listener.postLoad(entity);
        assertEquals("", entity.secret);
    }

    /** \u9a8c\u8bc1<code>entity.secret</code> \u7b49\u4e8e <code>"secret"</code>\u3002 */
    @Test
    void missingHandlerLeavesEncryptedFieldUntouched() {
        JpaEncryptFieldListener listener = new JpaEncryptFieldListener((Ddd4jFieldCryptoHandler) null);
        TestEntity entity = new TestEntity();
        entity.secret = "secret";

        listener.prePersist(entity);
        listener.postLoad(entity);

        assertEquals("secret", entity.secret);
    }

    /** \u5c06 <code>secret</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    static class BaseEntity {
        /** \u5f53\u524d\u7b7e\u540d\u3001\u52a0\u5bc6\u6216\u4ee4\u724c\u6821\u9a8c\u6d4b\u8bd5\u4f7f\u7528\u7684\u5bc6\u94a5\u6837\u672c\u3002 */
        @EncryptField
        String secret;
    }

    /** \u5c06 <code>plain</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    static final class TestEntity extends BaseEntity {
        /** \u7528\u4e8e\u52a0\u89e3\u5bc6\u5f80\u8fd4\u65ad\u8a00\u7684\u660e\u6587\u6837\u672c\u3002 */
        private String plain;
    }
}
