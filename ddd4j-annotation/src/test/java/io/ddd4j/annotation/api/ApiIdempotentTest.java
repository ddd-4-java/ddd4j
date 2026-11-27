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
package io.ddd4j.annotation.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ApiIdempotent} annotation defaults and overrides.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ApiIdempotentTest {

    /**
     * \u9a8c\u8bc1<code>annotation.value()</code>\u4e3a\u7a7a\uff0c<code>annotation.type()</code> \u7b49\u4e8e <code>ApiIdempotentType.ARGS</code>\uff0c<code>annotation.unlock()</code>\u4e3a\u5047\u3002
     * @throws NoSuchMethodException \u53cd\u5c04\u5b9a\u4f4d\u88ab\u6d4b\u65b9\u6cd5\u5931\u8d25
     */
    @Test
    void defaults_shouldHaveExpectedValues() throws NoSuchMethodException {
        ApiIdempotent annotation = annotationOf("defaultsEndpoint");

        assertThat(annotation.value()).isEmpty();
        assertThat(annotation.type()).isEqualTo(ApiIdempotentType.ARGS);
        assertThat(annotation.spel()).isFalse();
        assertThat(annotation.withArgs()).isFalse();
        assertThat(annotation.expireMillis()).isEqualTo(2000L);
        assertThat(annotation.retryTimes()).isZero();
        assertThat(annotation.retryInterval()).isEqualTo(100L);
        assertThat(annotation.unlock()).isFalse();
    }

    /**
     * \u9a8c\u8bc1<code>annotation.value()</code> \u7b49\u4e8e <code>"my-idempotent-key"</code>\u3002
     * @throws NoSuchMethodException \u53cd\u5c04\u5b9a\u4f4d\u88ab\u6d4b\u65b9\u6cd5\u5931\u8d25
     */
    @Test
    void value_shouldOverrideDefault() throws NoSuchMethodException {
        ApiIdempotent annotation = annotationOf("customValueEndpoint");

        assertThat(annotation.value()).isEqualTo("my-idempotent-key");
    }

    /**
     * \u9a8c\u8bc1<code>annotation.type()</code> \u7b49\u4e8e <code>ApiIdempotentType.TOKEN</code>\u3002
     * @throws NoSuchMethodException \u53cd\u5c04\u5b9a\u4f4d\u88ab\u6d4b\u65b9\u6cd5\u5931\u8d25
     */
    @Test
    void type_shouldOverrideToToken() throws NoSuchMethodException {
        ApiIdempotent annotation = annotationOf("tokenEndpoint");

        assertThat(annotation.type()).isEqualTo(ApiIdempotentType.TOKEN);
    }

    /**
     * \u9a8c\u8bc1<code>annotation.expireMillis()</code> \u7b49\u4e8e <code>5000L</code>\uff0c<code>annotation.retryTimes()</code> \u7b49\u4e8e <code>3</code>\uff0c<code>annotation.retryInterval()</code> \u7b49\u4e8e <code>200L</code>\u3002
     * @throws NoSuchMethodException \u53cd\u5c04\u5b9a\u4f4d\u88ab\u6d4b\u65b9\u6cd5\u5931\u8d25
     */
    @Test
    void expireAndRetry_shouldOverrideDefaults() throws NoSuchMethodException {
        ApiIdempotent annotation = annotationOf("configuredEndpoint");

        assertThat(annotation.expireMillis()).isEqualTo(5000L);
        assertThat(annotation.retryTimes()).isEqualTo(3);
        assertThat(annotation.retryInterval()).isEqualTo(200L);
    }

    /**
     * \u9a8c\u8bc1<code>annotation.spel()</code>\u4e3a\u771f\uff0c<code>annotation.withArgs()</code>\u4e3a\u771f\u3002
     * @throws NoSuchMethodException \u53cd\u5c04\u5b9a\u4f4d\u88ab\u6d4b\u65b9\u6cd5\u5931\u8d25
     */
    @Test
    void spelAndWithArgs_shouldBeOverridable() throws NoSuchMethodException {
        ApiIdempotent annotation = annotationOf("spelEndpoint");

        assertThat(annotation.spel()).isTrue();
        assertThat(annotation.withArgs()).isTrue();
    }

    /**
     * \u9a8c\u8bc1<code>annotation.unlock()</code>\u4e3a\u771f\u3002
     * @throws NoSuchMethodException \u53cd\u5c04\u5b9a\u4f4d\u88ab\u6d4b\u65b9\u6cd5\u5931\u8d25
     */
    @Test
    void unlock_shouldOverrideDefault() throws NoSuchMethodException {
        ApiIdempotent annotation = annotationOf("autoUnlockEndpoint");

        assertThat(annotation.unlock()).isTrue();
    }

    /** \u9a8c\u8bc1<code>ApiIdempotent.class.getAnnotation(java.lang.annotation.Retention.class).value()</code> \u7b49\u4e8e <code>java.lang.annotation.RetentionPolicy.RUNTIME</code>\u3002 */
    @Test
    void retention_shouldBeRuntime() {
        assertThat(ApiIdempotent.class.getAnnotation(java.lang.annotation.Retention.class).value())
                .isEqualTo(java.lang.annotation.RetentionPolicy.RUNTIME);
    }

    /** \u9a8c\u8bc1<code>target.value()</code> \u5305\u542b <code>java.lang.annotation.ElementType.METHOD</code>\u3001<code>java.lang.annotation.ElementType.TYPE</code>\u3002 */
    @Test
    void target_shouldIncludeMethodAndType() {
        java.lang.annotation.Target target = ApiIdempotent.class.getAnnotation(java.lang.annotation.Target.class);
        assertThat(target.value())
                .contains(java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.TYPE);
    }

    /**
     * \u4ece\u6307\u5b9a\u5939\u5177\u65b9\u6cd5\u8bfb\u53d6\u5e42\u7b49\u6ce8\u89e3\u3002
     * @param methodName \u5939\u5177\u65b9\u6cd5\u540d
     * @return \u65b9\u6cd5\u4e0a\u58f0\u660e\u7684\u5e42\u7b49\u6ce8\u89e3
     * @throws NoSuchMethodException \u5939\u5177\u4e2d\u627e\u4e0d\u5230\u6307\u5b9a\u7684\u65b9\u6cd5
     */
    private ApiIdempotent annotationOf(String methodName) throws NoSuchMethodException {
        Method method = Fixture.class.getDeclaredMethod(methodName);
        ApiIdempotent annotation = method.getAnnotation(ApiIdempotent.class);
        assertThat(annotation).as("@ApiIdempotent on " + methodName).isNotNull();
        return annotation;
    }

    /** \u4e3a\u53cd\u5c04\u6d4b\u8bd5\u63d0\u4f9b\u4f7f\u7528\u9ed8\u8ba4\u914d\u7f6e\u53ca\u81ea\u5b9a\u4e49\u5c5e\u6027\u7684\u5e42\u7b49\u6ce8\u89e3\u6837\u672c\u3002 */
    static class Fixture {

        /** \u4f7f\u7528\u5e42\u7b49\u6ce8\u89e3\u9ed8\u8ba4\u5c5e\u6027\u7684\u6d4b\u8bd5\u76ee\u6807\u3002 */
        @ApiIdempotent
        void defaultsEndpoint() {
        }

        /** \u63d0\u4f9b\u6307\u5b9a\u5e42\u7b49\u4e1a\u52a1\u952e\u7684\u6ce8\u89e3\u6837\u672c\u3002 */
        @ApiIdempotent("my-idempotent-key")
        void customValueEndpoint() {
        }

        /** \u63d0\u4f9b\u6309\u4ee4\u724c\u5224\u5b9a\u5e42\u7b49\u7684\u6ce8\u89e3\u6837\u672c\u3002 */
        @ApiIdempotent(type = ApiIdempotentType.TOKEN)
        void tokenEndpoint() {
        }

        /** \u63d0\u4f9b\u81ea\u5b9a\u4e49\u9501\u6709\u6548\u671f\u3001\u91cd\u8bd5\u6b21\u6570\u548c\u7b49\u5f85\u95f4\u9694\u7684\u6ce8\u89e3\u6837\u672c\u3002 */
        @ApiIdempotent(expireMillis = 5000, retryTimes = 3, retryInterval = 200)
        void configuredEndpoint() {
        }

        /** \u63d0\u4f9b\u542f\u7528 SpEL \u548c\u65b9\u6cd5\u5b9e\u53c2\u53c2\u4e0e\u5224\u5b9a\u7684\u6ce8\u89e3\u6837\u672c\u3002 */
        @ApiIdempotent(spel = true, withArgs = true)
        void spelEndpoint() {
        }

        /** \u63d0\u4f9b\u542f\u7528\u5904\u7406\u7ed3\u675f\u540e\u81ea\u52a8\u89e3\u9501\u7684\u6ce8\u89e3\u6837\u672c\u3002 */
        @ApiIdempotent(unlock = true)
        void autoUnlockEndpoint() {
        }
    }
}
