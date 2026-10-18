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

    @Test
    void missingHandlerLeavesEncryptedFieldUntouched() {
        JpaEncryptFieldListener listener = new JpaEncryptFieldListener((Ddd4jFieldCryptoHandler) null);
        TestEntity entity = new TestEntity();
        entity.secret = "secret";

        listener.prePersist(entity);
        listener.postLoad(entity);

        assertEquals("secret", entity.secret);
    }

    static class BaseEntity {
        @EncryptField
        String secret;
    }

    static final class TestEntity extends BaseEntity {
        private String plain;
    }
}
