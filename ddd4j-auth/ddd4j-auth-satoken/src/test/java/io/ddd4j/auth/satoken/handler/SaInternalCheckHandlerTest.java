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
     * 通过 <code>method()</code> 取得当前测试需要的结果。
     *
     * @param methodName 传给 <code>method()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>method(methodName).getAnnotation(SaInternalCheck.class)</code>
     * @throws NoSuchMethodException 反射定位被测方法失败
     */
    private static SaInternalCheck annotation(String methodName) throws NoSuchMethodException {
        return method(methodName).getAnnotation(SaInternalCheck.class);
    }

    /**
     * 通过 <code>Fixture.class.getDeclaredMethod()</code> 取得当前测试需要的结果。
     *
     * @param methodName 传给 <code>Fixture.class.getDeclaredMethod()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>Fixture.class.getDeclaredMethod(methodName)</code>
     * @throws NoSuchMethodException 反射定位被测方法失败
     */
    private static AnnotatedElement method(String methodName) throws NoSuchMethodException {
        return Fixture.class.getDeclaredMethod(methodName);
    }

    /**
     * 验证<code>handler.checkMethod(annotation, method("internalEndpoint"))</code> 抛出 <code>SaTokenException.class</code>，<code>ex.getMessage()</code> 等于 <code>"Missing internal API key"</code>。
     *
     * @throws NoSuchMethodException 反射定位被测方法失败
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
     * 验证<code>handler.checkedApiKey</code> 等于 <code>"key-1"</code>。
     *
     * @throws NoSuchMethodException 反射定位被测方法失败
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
     * 验证<code>handler.checkedApiKey</code> 等于 <code>"key-2"</code>。
     *
     * @throws NoSuchMethodException 反射定位被测方法失败
     */
    @Test
    void shouldCheckCustomScopes() throws NoSuchMethodException {
        TestSaInternalCheckHandler handler = new TestSaInternalCheckHandler("key-2");
        SaInternalCheck annotation = annotation("customInternalEndpoint");

        assertDoesNotThrow(() -> handler.checkMethod(annotation, method("customInternalEndpoint")));

        assertEquals("key-2", handler.checkedApiKey);
        assertArrayEquals(new String[]{"internal", "mq"}, handler.checkedScopes);
    }

    /**
     * 验证<code>handler.getHandlerAnnotationClass()</code> 等于 <code>SaInternalCheck.class</code>。
     */
    @Test
    void shouldExposeHandledAnnotationClass() {
        SaInternalCheckHandler handler = new SaInternalCheckHandler();

        assertEquals(SaInternalCheck.class, handler.getHandlerAnnotationClass());
    }

    /**
     * 实现 <code>SaInternalCheckHandler</code>，供测试调用 <code>readApiKey()</code>、<code>checkApiKey()</code> 并观察结果。
     */
    private static class TestSaInternalCheckHandler extends SaInternalCheckHandler {

        /**
         * 内部 API 认证测试使用的凭据。
         */
        private final String apiKey;
        /**
         * 用于断言传给检查器的 API 凭据。
         */
        private String checkedApiKey;
        /**
         * 用于断言检查器收到的访问范围。
         */
        private String[] checkedScopes;

        /**
         * 以给定状态构造 <code>TestSaInternalCheckHandler</code> 测试对象。
         *
         * @param apiKey 内部 API 认证测试使用的凭据
         */
        private TestSaInternalCheckHandler(String apiKey) {
            this.apiKey = apiKey;
        }

        /**
         * 从当前请求或上下文中提取 API 密钥。
         *
         * @return 实际返回<code>apiKey</code>，供调用方取得当前值
         */
        @Override
        protected String readApiKey() {
            return apiKey;
        }

        /**
         * 验证请求提交的 API 密钥。
         *
         * <p>测试夹具会调用 <code>Arrays.copyOf(scopes, scopes.length)</code>。</p>
         *
         * @param apiKey API Key to validate
         * @param scopes required scopes
         */
        @Override
        protected void checkApiKey(String apiKey, String[] scopes) {
            this.checkedApiKey = apiKey;
            this.checkedScopes = Arrays.copyOf(scopes, scopes.length);
        }
    }

    /**
     * 提供 <code>internalEndpoint()</code>、<code>customInternalEndpoint()</code> 等供当前测试使用的独立操作。
     */
    private static class Fixture {

        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         */
        @SaInternalCheck
        void internalEndpoint() {
        }

        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         */
        @SaInternalCheck(scope = {"internal", "mq"})
        void customInternalEndpoint() {
        }
    }
}
