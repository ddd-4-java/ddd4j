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

    /** \u7528\u4e8e\u9694\u79bb <code>ProceedingJoinPoint</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private ProceedingJoinPoint joinPoint;

    /** \u7528\u4e8e\u9694\u79bb <code>MethodSignature</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private MethodSignature methodSignature;

    // =================== 固定 value 模式 ===================

    /**
     * \u9a8c\u8bc1<code>key</code> \u7b49\u4e8e <code>"my-fixed-key"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>key</code> \u7b49\u4e8e <code>"hello-42"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>key</code> \u7684\u5927\u5c0f\u4e3a <code>32</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>key</code> \u7684\u5927\u5c0f\u4e3a <code>32</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>key1</code> \u4e0d\u7b49\u4e8e <code>key2</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>key1</code> \u7b49\u4e8e <code>key2</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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

    /** \u4e3a HTTP \u5408\u540c\u63d0\u4f9b\u5b9e\u9645\u8bf7\u6c42\u8def\u7531\u7684 Spring \u6d4b\u8bd5\u63a7\u5236\u5668\u3002 */
    @RestController
    @RequestMapping("/api/sample")
    static class SampleController {

        /**
         * \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         */
        @ApiIdempotent("my-fixed-key")
        @PostMapping("/fixed")
        public void fixedValueEndpoint(String name) {
        }

        /**
         * \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         * @param count \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>Integer</code> \u53c2\u6570 <code>count</code>
         */
        @ApiIdempotent(value = "#name + '-' + #count", spel = true)
        @PostMapping("/spel")
        public void spelEndpoint(String name, Integer count) {
        }

        /**
         * \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002
         * @param data \u5f53\u524d\u7528\u4f8b\u5199\u5165\u3001\u8bfb\u53d6\u6216\u6bd4\u8f83\u7684\u6570\u636e
         */
        @ApiIdempotent
        @PostMapping("/default")
        public void defaultEndpoint(String data) {
        }

        /** \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002 */
        @ApiIdempotent
        @PostMapping("/noargs")
        public void noArgsEndpoint() {
        }

        /**
         * \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002
         * @param orderId \u6d4b\u8bd5\u4e2d\u8981\u5b9a\u4f4d\u7684\u8ba2\u5355\u6807\u8bc6
         */
        @ApiIdempotent(withArgs = true)
        @PostMapping("/withargs")
        public void withArgsEndpoint(String orderId) {
        }
    }
}
