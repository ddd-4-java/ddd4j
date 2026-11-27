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
package io.ddd4j.web.webmvc;

import feign.Contract;
import feign.RequestLine;
import feign.RequestTemplate;
import feign.Target;
import io.ddd4j.core.BaseCoreProperties;
import io.ddd4j.core.api.R;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.web.webmvc.annotation.FeignHeader;
import io.ddd4j.web.webmvc.config.BaseWebConfig;
import io.ddd4j.web.webmvc.config.ServerI18nProperties;
import io.ddd4j.web.webmvc.error.GlobalExceptionHandler;
import io.ddd4j.web.webmvc.interceptor.BaseWebInterceptor;
import io.ddd4j.web.webmvc.interceptor.FeignHeaderInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static io.ddd4j.core.constant.ContextConstants.SYSTEM_ID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 同输入验证跨版本 Web 注册与出站 header；不启动外部服务。
 */
class ThreeLineWebParityTest {
    /**
     * \u8c03\u7528 <code>MappedInterceptor.class.getMethod()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param interceptor \u4f20\u7ed9 <code>MappedInterceptor.class.getMethod()</code> \u7684 <code>MappedInterceptor</code> \u8f93\u5165
     * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>(String[])MappedInterceptor.class.getMethod("getIncludePathPatterns").invoke(interceptor)</code>\u3001<code>(String[])MappedInterceptor.class.getMethod("getPathPatterns").invoke(interceptor)</code>
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
    private static String[] includedPatterns(MappedInterceptor interceptor) throws Exception {
        // Spring 7 移除了旧 getter，测试观察同一注册结果。
        try {
            return (String[]) MappedInterceptor.class.getMethod("getIncludePathPatterns").invoke(interceptor);
        } catch (NoSuchMethodException exception) {
            return (String[]) MappedInterceptor.class.getMethod("getPathPatterns").invoke(interceptor);
        }
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>ThreadContext.clear()</code>\u3001<code>RequestContextHolder.resetRequestAttributes()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        ThreadContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * \u9a8c\u8bc1<code>registry.entries().size()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>mapped.getInterceptor()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>interceptor</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void nonEmptyInterceptorsAreRegisteredWithTheirPaths() throws Exception {
        BaseWebInterceptor interceptor = new BaseWebInterceptor() {
            @Override
            public int getOrder() {
                return 0;
            }

            @Override
            public String[] pathPatterns() {
                return new String[]{"/protected/**"};
            }

            @Override
            public String[] excludePathPatterns() {
                return new String[]{"/protected/skipped"};
            }
        };
        BaseWebInterceptor second = new BaseWebInterceptor() {
            @Override
            public int getOrder() {
                return 1;
            }
        };
        BaseWebConfig config = new BaseWebConfig(java.util.Arrays.asList(interceptor, second),
                new BaseCoreProperties(), Collections.emptyList());
        RecordingRegistry registry = new RecordingRegistry();
        config.addInterceptors(registry);
        assertEquals(2, registry.entries().size(), "all non-empty interceptors must be registered");
        MappedInterceptor mapped = (MappedInterceptor) registry.entries().get(0);
        assertSame(interceptor, mapped.getInterceptor());
        assertArrayEquals(new String[]{"/protected/**"}, includedPatterns(mapped));
        assertSame(second, ((MappedInterceptor) registry.entries().get(1)).getInterceptor());
        MockHttpServletRequest included = new MockHttpServletRequest("GET", "/protected/ok");
        ServletRequestPathUtils.parseAndCache(included);
        assertTrue(mapped.matches(included));
        MockHttpServletRequest excluded = new MockHttpServletRequest("GET", "/protected/skipped");
        ServletRequestPathUtils.parseAndCache(excluded);
        assertFalse(mapped.matches(excluded));
    }

    /** \u9a8c\u8bc1<code>registry.entries().isEmpty()</code>\u4e3a\u771f\u3002 */
    @Test
    void emptyAndNullInterceptorListsAreSafe() {
        RecordingRegistry registry = new RecordingRegistry();
        new BaseWebConfig(Collections.emptyList(), new BaseCoreProperties(), Collections.emptyList()).addInterceptors(registry);
        new BaseWebConfig(null, new BaseCoreProperties(), Collections.emptyList()).addInterceptors(registry);
        assertTrue(registry.entries().isEmpty());
    }

    /**
     * \u9a8c\u8bc1<code>template.headers().get(name)</code>\u975e\u7a7a\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void systemIdDefaultsForNullAndEmptyButPreservesAValue() throws Exception {
        for (String value : new String[]{null, "", "tenant-system"}) {
            ThreadContext.clear();
            if (Objects.nonNull(value)) {
                ThreadContext.put(SYSTEM_ID, value);
            }
            RequestTemplate template = new RequestTemplate();
            template.methodMetadata(new Contract.Default().parseAndValidateMetadata(HeaderClient.class).get(0));
            template.feignTarget(new Target.HardCodedTarget<HeaderClient>(HeaderClient.class, "parity", "http://localhost"));
            new FeignHeaderInterceptor().apply(template);
            String expected = Objects.isNull(value) || value.isEmpty() ? "0" : "tenant-system";
            for (String name : FeignHeaderInterceptor.HEADER_SYSTEM_IDS) {
                assertNotNull(template.headers().get(name), name + " must be present");
                assertEquals(Collections.singletonList(expected), new java.util.ArrayList<String>(template.headers().get(name)), name);
            }
        }
    }

    /**
     * \u9a8c\u8bc1<code>response.getCode()</code> \u7b49\u4e8e <code>400</code>\uff0c<code>response.getMsg()</code> \u7b49\u4e8e <code>enabled ? "localized header message" : "\u7f3a\u5c11\u8bf7\u6c42\u5934: [X-Parity]."</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
    @Test
    void missingHeaderResponsePreservesStatusAndMessageWithOrWithoutI18n() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler() {
            @Override
            protected void logException(Exception exception) {
            }

            @Override
            protected String getLocaleMessage(Exception ex, String key, String fallback) {
                return "localized header message";
            }
        };
        ServerI18nProperties properties = new ServerI18nProperties();
        ReflectionTestUtils.setField(handler, "serverI18NProperties", properties);
        MissingRequestHeaderException exception = new MissingRequestHeaderException("X-Parity",
                new MethodParameter(ThreeLineWebParityTest.class.getDeclaredMethod("headerEndpoint", String.class), 0));
        for (boolean enabled : new boolean[]{false, true}) {
            properties.setEnabled(enabled);
            R<String> response = handler.missingRequestHeaderException(exception);
            assertEquals(400, response.getCode());
            assertEquals(enabled ? "localized header message" : "缺少请求头: [X-Parity].", response.getMsg());
        }
    }

    /**
     * \u4f5c\u4e3a\u7a7a\u5b9e\u73b0\u7684\u6d4b\u8bd5\u66ff\u8eab\uff0c\u8c03\u7528\u65f6\u4fdd\u6301\u5f53\u524d\u5939\u5177\u72b6\u6001\u3002
     * @param value \u5f53\u524d\u7528\u4f8b\u4f20\u5165\u7684\u503c
     */
    private void headerEndpoint(String value) {
    }

    /** \u63d0\u4f9b <code>request()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    interface HeaderClient {
        /** \u5411\u5408\u540c\u6d4b\u8bd5\u670d\u52a1\u6839\u8def\u5f84\u53d1\u51fa\u4e0d\u81ea\u52a8\u900f\u4f20\u8bf7\u6c42\u5934\u548c\u79df\u6237\u6807\u8bc6\u7684 Feign GET \u8bf7\u6c42\u3002 */
        @RequestLine("GET /")
        @FeignHeader(autoFillTenantId = false, useWebRequestHeader = false)
        void request();
    }

    /** \u5b9e\u73b0 <code>InterceptorRegistry</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>entries()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static class RecordingRegistry extends InterceptorRegistry {
        /**
         * \u901a\u8fc7 <code>getInterceptors()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>getInterceptors()</code>
         */
        List<Object> entries() {
            return getInterceptors();
        }
    }
}
