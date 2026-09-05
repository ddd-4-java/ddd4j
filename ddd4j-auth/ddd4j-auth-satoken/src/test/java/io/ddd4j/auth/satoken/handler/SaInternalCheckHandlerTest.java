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
package io.ddd4j.auth.satoken.handler;

import cn.dev33.satoken.exception.SaTokenException;
import io.ddd4j.auth.satoken.annotation.SaInternalCheck;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SaInternalCheckHandler} tests.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class SaInternalCheckHandlerTest {

    private static SaInternalCheck annotation(String methodName) throws NoSuchMethodException {
        return method(methodName).getAnnotation(SaInternalCheck.class);
    }

    private static AnnotatedElement method(String methodName) throws NoSuchMethodException {
        return Fixture.class.getDeclaredMethod(methodName);
    }

    @Test
    void shouldRejectMissingApiKey() throws NoSuchMethodException {
        SaInternalCheckHandler handler = new TestSaInternalCheckHandler("");
        SaInternalCheck annotation = annotation("internalEndpoint");

        SaTokenException ex = assertThrows(SaTokenException.class,
                () -> handler.checkMethod(annotation, method("internalEndpoint")));

        assertEquals("Missing internal API key", ex.getMessage());
    }

    @Test
    void shouldCheckDefaultInternalScope() throws NoSuchMethodException {
        TestSaInternalCheckHandler handler = new TestSaInternalCheckHandler("key-1");
        SaInternalCheck annotation = annotation("internalEndpoint");

        assertDoesNotThrow(() -> handler.checkMethod(annotation, method("internalEndpoint")));

        assertEquals("key-1", handler.checkedApiKey);
        assertArrayEquals(new String[]{"internal"}, handler.checkedScopes);
    }

    @Test
    void shouldCheckCustomScopes() throws NoSuchMethodException {
        TestSaInternalCheckHandler handler = new TestSaInternalCheckHandler("key-2");
        SaInternalCheck annotation = annotation("customInternalEndpoint");

        assertDoesNotThrow(() -> handler.checkMethod(annotation, method("customInternalEndpoint")));

        assertEquals("key-2", handler.checkedApiKey);
        assertArrayEquals(new String[]{"internal", "mq"}, handler.checkedScopes);
    }

    @Test
    void shouldExposeHandledAnnotationClass() {
        SaInternalCheckHandler handler = new SaInternalCheckHandler();

        assertEquals(SaInternalCheck.class, handler.getHandlerAnnotationClass());
    }

    private static class TestSaInternalCheckHandler extends SaInternalCheckHandler {

        private final String apiKey;
        private String checkedApiKey;
        private String[] checkedScopes;

        private TestSaInternalCheckHandler(String apiKey) {
            this.apiKey = apiKey;
        }

        @Override
        protected String readApiKey() {
            return apiKey;
        }

        @Override
        protected void checkApiKey(String apiKey, String[] scopes) {
            this.checkedApiKey = apiKey;
            this.checkedScopes = Arrays.copyOf(scopes, scopes.length);
        }
    }

    private static class Fixture {

        @SaInternalCheck
        void internalEndpoint() {
        }

        @SaInternalCheck(scope = {"internal", "mq"})
        void customInternalEndpoint() {
        }
    }
}
