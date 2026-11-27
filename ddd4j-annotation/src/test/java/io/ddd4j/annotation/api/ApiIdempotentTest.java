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
     * 验证<code>annotation.value()</code>为空，<code>annotation.type()</code> 等于 <code>ApiIdempotentType.ARGS</code>，<code>annotation.unlock()</code>为假。
     *
     * @throws NoSuchMethodException 反射定位被测方法失败
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
     * 验证<code>annotation.value()</code> 等于 <code>"my-idempotent-key"</code>。
     *
     * @throws NoSuchMethodException 反射定位被测方法失败
     */
    @Test
    void value_shouldOverrideDefault() throws NoSuchMethodException {
        ApiIdempotent annotation = annotationOf("customValueEndpoint");

        assertThat(annotation.value()).isEqualTo("my-idempotent-key");
    }

    /**
     * 验证<code>annotation.type()</code> 等于 <code>ApiIdempotentType.TOKEN</code>。
     *
     * @throws NoSuchMethodException 反射定位被测方法失败
     */
    @Test
    void type_shouldOverrideToToken() throws NoSuchMethodException {
        ApiIdempotent annotation = annotationOf("tokenEndpoint");

        assertThat(annotation.type()).isEqualTo(ApiIdempotentType.TOKEN);
    }

    /**
     * 验证<code>annotation.expireMillis()</code> 等于 <code>5000L</code>，<code>annotation.retryTimes()</code> 等于 <code>3</code>，<code>annotation.retryInterval()</code> 等于 <code>200L</code>。
     *
     * @throws NoSuchMethodException 反射定位被测方法失败
     */
    @Test
    void expireAndRetry_shouldOverrideDefaults() throws NoSuchMethodException {
        ApiIdempotent annotation = annotationOf("configuredEndpoint");

        assertThat(annotation.expireMillis()).isEqualTo(5000L);
        assertThat(annotation.retryTimes()).isEqualTo(3);
        assertThat(annotation.retryInterval()).isEqualTo(200L);
    }

    /**
     * 验证<code>annotation.spel()</code>为真，<code>annotation.withArgs()</code>为真。
     *
     * @throws NoSuchMethodException 反射定位被测方法失败
     */
    @Test
    void spelAndWithArgs_shouldBeOverridable() throws NoSuchMethodException {
        ApiIdempotent annotation = annotationOf("spelEndpoint");

        assertThat(annotation.spel()).isTrue();
        assertThat(annotation.withArgs()).isTrue();
    }

    /**
     * 验证<code>annotation.unlock()</code>为真。
     *
     * @throws NoSuchMethodException 反射定位被测方法失败
     */
    @Test
    void unlock_shouldOverrideDefault() throws NoSuchMethodException {
        ApiIdempotent annotation = annotationOf("autoUnlockEndpoint");

        assertThat(annotation.unlock()).isTrue();
    }

    /**
     * 验证<code>ApiIdempotent.class.getAnnotation(java.lang.annotation.Retention.class).value()</code> 等于 <code>java.lang.annotation.RetentionPolicy.RUNTIME</code>。
     */
    @Test
    void retention_shouldBeRuntime() {
        assertThat(ApiIdempotent.class.getAnnotation(java.lang.annotation.Retention.class).value())
                .isEqualTo(java.lang.annotation.RetentionPolicy.RUNTIME);
    }

    /**
     * 验证<code>target.value()</code> 包含 <code>java.lang.annotation.ElementType.METHOD</code>、<code>java.lang.annotation.ElementType.TYPE</code>。
     */
    @Test
    void target_shouldIncludeMethodAndType() {
        java.lang.annotation.Target target = ApiIdempotent.class.getAnnotation(java.lang.annotation.Target.class);
        assertThat(target.value())
                .contains(java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.TYPE);
    }

    /**
     * 从指定夹具方法读取幂等注解。
     *
     * @param methodName 夹具方法名
     * @return 方法上声明的幂等注解
     * @throws NoSuchMethodException 夹具中找不到指定的方法
     */
    private ApiIdempotent annotationOf(String methodName) throws NoSuchMethodException {
        Method method = Fixture.class.getDeclaredMethod(methodName);
        ApiIdempotent annotation = method.getAnnotation(ApiIdempotent.class);
        assertThat(annotation).as("@ApiIdempotent on " + methodName).isNotNull();
        return annotation;
    }

    /**
     * 为反射测试提供使用默认配置及自定义属性的幂等注解样本。
     */
    static class Fixture {

        /**
         * 使用幂等注解默认属性的测试目标。
         */
        @ApiIdempotent
        void defaultsEndpoint() {
        }

        /**
         * 提供指定幂等业务键的注解样本。
         */
        @ApiIdempotent("my-idempotent-key")
        void customValueEndpoint() {
        }

        /**
         * 提供按令牌判定幂等的注解样本。
         */
        @ApiIdempotent(type = ApiIdempotentType.TOKEN)
        void tokenEndpoint() {
        }

        /**
         * 提供自定义锁有效期、重试次数和等待间隔的注解样本。
         */
        @ApiIdempotent(expireMillis = 5000, retryTimes = 3, retryInterval = 200)
        void configuredEndpoint() {
        }

        /**
         * 提供启用 SpEL 和方法实参参与判定的注解样本。
         */
        @ApiIdempotent(spel = true, withArgs = true)
        void spelEndpoint() {
        }

        /**
         * 提供启用处理结束后自动解锁的注解样本。
         */
        @ApiIdempotent(unlock = true)
        void autoUnlockEndpoint() {
        }
    }
}
