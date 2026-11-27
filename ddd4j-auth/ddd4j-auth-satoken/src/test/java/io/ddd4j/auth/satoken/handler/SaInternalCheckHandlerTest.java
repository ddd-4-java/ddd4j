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

    /**
     * \u901a\u8fc7 <code>method()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param methodName \u4f20\u7ed9 <code>method()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>method(methodName).getAnnotation(SaInternalCheck.class)</code>
     * @throws NoSuchMethodException \u53cd\u5c04\u5b9a\u4f4d\u88ab\u6d4b\u65b9\u6cd5\u5931\u8d25
     */
    private static SaInternalCheck annotation(String methodName) throws NoSuchMethodException {
        return method(methodName).getAnnotation(SaInternalCheck.class);
    }

    /**
     * \u901a\u8fc7 <code>Fixture.class.getDeclaredMethod()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param methodName \u4f20\u7ed9 <code>Fixture.class.getDeclaredMethod()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Fixture.class.getDeclaredMethod(methodName)</code>
     * @throws NoSuchMethodException \u53cd\u5c04\u5b9a\u4f4d\u88ab\u6d4b\u65b9\u6cd5\u5931\u8d25
     */
    private static AnnotatedElement method(String methodName) throws NoSuchMethodException {
        return Fixture.class.getDeclaredMethod(methodName);
    }

    /**
     * \u9a8c\u8bc1<code>handler.checkMethod(annotation, method("internalEndpoint"))</code> \u629b\u51fa <code>SaTokenException.class</code>\uff0c<code>ex.getMessage()</code> \u7b49\u4e8e <code>"Missing internal API key"</code>\u3002
     * @throws NoSuchMethodException \u53cd\u5c04\u5b9a\u4f4d\u88ab\u6d4b\u65b9\u6cd5\u5931\u8d25
     */
    @Test
    void shouldRejectMissingApiKey() throws NoSuchMethodException {
        SaInternalCheckHandler handler = new TestSaInternalCheckHandler("");
        SaInternalCheck annotation = annotation("internalEndpoint");

        SaTokenException ex = assertThrows(SaTokenException.class,
                () -> handler.checkMethod(annotation, method("internalEndpoint")));

        assertEquals("Missing internal API key", ex.getMessage());
    }

    /**
     * \u9a8c\u8bc1<code>handler.checkedApiKey</code> \u7b49\u4e8e <code>"key-1"</code>\u3002
     * @throws NoSuchMethodException \u53cd\u5c04\u5b9a\u4f4d\u88ab\u6d4b\u65b9\u6cd5\u5931\u8d25
     */
    @Test
    void shouldCheckDefaultInternalScope() throws NoSuchMethodException {
        TestSaInternalCheckHandler handler = new TestSaInternalCheckHandler("key-1");
        SaInternalCheck annotation = annotation("internalEndpoint");

        assertDoesNotThrow(() -> handler.checkMethod(annotation, method("internalEndpoint")));

        assertEquals("key-1", handler.checkedApiKey);
        assertArrayEquals(new String[]{"internal"}, handler.checkedScopes);
    }

    /**
     * \u9a8c\u8bc1<code>handler.checkedApiKey</code> \u7b49\u4e8e <code>"key-2"</code>\u3002
     * @throws NoSuchMethodException \u53cd\u5c04\u5b9a\u4f4d\u88ab\u6d4b\u65b9\u6cd5\u5931\u8d25
     */
    @Test
    void shouldCheckCustomScopes() throws NoSuchMethodException {
        TestSaInternalCheckHandler handler = new TestSaInternalCheckHandler("key-2");
        SaInternalCheck annotation = annotation("customInternalEndpoint");

        assertDoesNotThrow(() -> handler.checkMethod(annotation, method("customInternalEndpoint")));

        assertEquals("key-2", handler.checkedApiKey);
        assertArrayEquals(new String[]{"internal", "mq"}, handler.checkedScopes);
    }

    /** \u9a8c\u8bc1<code>handler.getHandlerAnnotationClass()</code> \u7b49\u4e8e <code>SaInternalCheck.class</code>\u3002 */
    @Test
    void shouldExposeHandledAnnotationClass() {
        SaInternalCheckHandler handler = new SaInternalCheckHandler();

        assertEquals(SaInternalCheck.class, handler.getHandlerAnnotationClass());
    }

    /** \u5b9e\u73b0 <code>SaInternalCheckHandler</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>readApiKey()</code>\u3001<code>checkApiKey()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static class TestSaInternalCheckHandler extends SaInternalCheckHandler {

        /** \u5185\u90e8 API \u8ba4\u8bc1\u6d4b\u8bd5\u4f7f\u7528\u7684\u51ed\u636e\u3002 */
        private final String apiKey;
        /** \u7528\u4e8e\u65ad\u8a00\u4f20\u7ed9\u68c0\u67e5\u5668\u7684 API \u51ed\u636e\u3002 */
        private String checkedApiKey;
        /** \u7528\u4e8e\u65ad\u8a00\u68c0\u67e5\u5668\u6536\u5230\u7684\u8bbf\u95ee\u8303\u56f4\u3002 */
        private String[] checkedScopes;

        /**
         * \u4ee5\u7ed9\u5b9a\u72b6\u6001\u6784\u9020 <code>TestSaInternalCheckHandler</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002
         * @param apiKey \u5185\u90e8 API \u8ba4\u8bc1\u6d4b\u8bd5\u4f7f\u7528\u7684\u51ed\u636e
         */
        private TestSaInternalCheckHandler(String apiKey) {
            this.apiKey = apiKey;
        }

        /** {@inheritDoc} */
        @Override
        protected String readApiKey() {
            return apiKey;
        }

        /** {@inheritDoc} */
        @Override
        protected void checkApiKey(String apiKey, String[] scopes) {
            this.checkedApiKey = apiKey;
            this.checkedScopes = Arrays.copyOf(scopes, scopes.length);
        }
    }

    /** \u63d0\u4f9b <code>internalEndpoint()</code>\u3001<code>customInternalEndpoint()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    private static class Fixture {

        /** \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002 */
        @SaInternalCheck
        void internalEndpoint() {
        }

        /** \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002 */
        @SaInternalCheck(scope = {"internal", "mq"})
        void customInternalEndpoint() {
        }
    }
}
