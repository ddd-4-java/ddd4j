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
package io.ddd4j.web.webmvc.util;

import io.ddd4j.annotation.api.ApiIdempotent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link IdempotentKit} 幂等键生成单元测试。
 *
 * <p>验证 {@link IdempotentKit#getIdempotentKey(ProceedingJoinPoint, ApiIdempotent)} 的三种模式：
 * <ul>
 *   <li>固定 value 模式：直接返回注解 value</li>
 *   <li>SpEL 模式：解析表达式返回值</li>
 *   <li>路由拼接模式：拼接 RequestMapping + PostMapping 路径后计算 MD5</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ExtendWith(MockitoExtension.class)
class IdempotentKitTest {

    /**
     * 用于隔离 <code>ProceedingJoinPoint</code> 调用并记录交互的测试替身。
     */
    @Mock
    private ProceedingJoinPoint joinPoint;

    /**
     * 用于隔离 <code>MethodSignature</code> 调用并记录交互的测试替身。
     */
    @Mock
    private MethodSignature methodSignature;

    // =================== 固定 value 模式 ===================

    /**
     * 验证<code>key</code> 等于 <code>"my-fixed-key"</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getIdempotentKey_withFixedValue_shouldReturnDirectly() throws Exception {
        Method method = SampleController.class.getMethod("fixedValueEndpoint", String.class);
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"name"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"test"});

        String key = IdempotentKit.getIdempotentKey(joinPoint, idempotent);

        assertThat(key).isEqualTo("my-fixed-key");
    }

    // =================== SpEL 模式 ===================

    /**
     * 验证<code>key</code> 等于 <code>"hello-42"</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getIdempotentKey_withSpel_shouldEvaluateExpression() throws Exception {
        Method method = SampleController.class.getMethod("spelEndpoint", String.class, Integer.class);
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"name", "count"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"hello", 42});

        String key = IdempotentKit.getIdempotentKey(joinPoint, idempotent);

        assertThat(key).isEqualTo("hello-42");
    }

    // =================== 路由拼接模式（默认） ===================

    /**
     * 验证<code>key</code> 的大小为 <code>32</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getIdempotentKey_withRouteMapping_shouldGenerateMd5() throws Exception {
        Method method = SampleController.class.getMethod("defaultEndpoint", String.class);
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"data"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"payload"});

        String key = IdempotentKit.getIdempotentKey(joinPoint, idempotent);

        // 应该返回 MD5 哈希（32 位十六进制）
        assertThat(key).hasSize(32);
        assertThat(key).matches("[a-f0-9]+");
    }

    /**
     * 验证<code>key</code> 的大小为 <code>32</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getIdempotentKey_withRouteMapping_noArgs_shouldGenerateMd5() throws Exception {
        Method method = SampleController.class.getMethod("noArgsEndpoint");
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{});
        when(joinPoint.getArgs()).thenReturn(new Object[]{});

        String key = IdempotentKit.getIdempotentKey(joinPoint, idempotent);

        assertThat(key).hasSize(32);
        assertThat(key).matches("[a-f0-9]+");
    }

    /**
     * 验证<code>key1</code> 不等于 <code>key2</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getIdempotentKey_withArgsIncluded_shouldVaryByArgs() throws Exception {
        Method method = SampleController.class.getMethod("withArgsEndpoint", String.class);
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"orderId"});

        // 不同参数应生成不同 key
        when(joinPoint.getArgs()).thenReturn(new Object[]{"ORD-001"});
        String key1 = IdempotentKit.getIdempotentKey(joinPoint, idempotent);

        when(joinPoint.getArgs()).thenReturn(new Object[]{"ORD-002"});
        String key2 = IdempotentKit.getIdempotentKey(joinPoint, idempotent);

        assertThat(key1).isNotEqualTo(key2);
    }

    /**
     * 验证<code>key1</code> 等于 <code>key2</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getIdempotentKey_sameArgsSameRoute_shouldGenerateSameKey() throws Exception {
        Method method = SampleController.class.getMethod("defaultEndpoint", String.class);
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"data"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"same-data"});

        String key1 = IdempotentKit.getIdempotentKey(joinPoint, idempotent);
        String key2 = IdempotentKit.getIdempotentKey(joinPoint, idempotent);

        assertThat(key1).isEqualTo(key2);
    }

    // =================== 辅助：测试用 Controller ===================

    /**
     * 为 HTTP 合同提供实际请求路由的 Spring 测试控制器。
     */
    @RestController
    @RequestMapping("/api/sample")
    static class SampleController {

        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         *
         * @param name 当前测试指定的对象或属性名称
         */
        @ApiIdempotent("my-fixed-key")
        @PostMapping("/fixed")
        public void fixedValueEndpoint(String name) {
        }

        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         *
         * @param name  当前测试指定的对象或属性名称
         * @param count 本次调用使用的 <code>Integer</code> 参数 <code>count</code>
         */
        @ApiIdempotent(value = "#name + '-' + #count", spel = true)
        @PostMapping("/spel")
        public void spelEndpoint(String name, Integer count) {
        }

        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         *
         * @param data 当前用例写入、读取或比较的数据
         */
        @ApiIdempotent
        @PostMapping("/default")
        public void defaultEndpoint(String data) {
        }

        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         */
        @ApiIdempotent
        @PostMapping("/noargs")
        public void noArgsEndpoint() {
        }

        /**
         * 作为空实现的测试替身，调用时保持当前夹具状态。
         *
         * @param orderId 测试中要定位的订单标识
         */
        @ApiIdempotent(withArgs = true)
        @PostMapping("/withargs")
        public void withArgsEndpoint(String orderId) {
        }
    }
}
