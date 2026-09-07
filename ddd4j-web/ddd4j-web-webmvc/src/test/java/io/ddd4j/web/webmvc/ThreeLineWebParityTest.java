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
import io.ddd4j.core.ApiRestResponse;
import io.ddd4j.core.BaseCoreProperties;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
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

/** 同输入验证跨版本 Web 注册与出站 header；不启动外部服务。 */
class ThreeLineWebParityTest {
    @AfterEach
    void clearContext() {
        ThreadContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void nonEmptyInterceptorsAreRegisteredWithTheirPaths() throws Exception {
        BaseWebInterceptor interceptor = new BaseWebInterceptor() {
            @Override public int getOrder() { return 0; }
            @Override public String[] pathPatterns() { return new String[]{"/protected/**"}; }
            @Override public String[] excludePathPatterns() { return new String[]{"/protected/skipped"}; }
        };
        BaseWebInterceptor second = new BaseWebInterceptor() {
            @Override public int getOrder() { return 1; }
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

    @Test
    void emptyAndNullInterceptorListsAreSafe() {
        RecordingRegistry registry = new RecordingRegistry();
        new BaseWebConfig(Collections.emptyList(), new BaseCoreProperties(), Collections.emptyList()).addInterceptors(registry);
        new BaseWebConfig(null, new BaseCoreProperties(), Collections.emptyList()).addInterceptors(registry);
        assertTrue(registry.entries().isEmpty());
    }

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

    @Test
    void missingHeaderResponsePreservesStatusAndMessageWithOrWithoutI18n() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler() {
            @Override protected void logException(Exception exception) { }
            @Override protected String getLocaleMessage(Exception ex, String key, String fallback) {
                return "localized header message";
            }
        };
        ServerI18nProperties properties = new ServerI18nProperties();
        ReflectionTestUtils.setField(handler, "serverI18NProperties", properties);
        MissingRequestHeaderException exception = new MissingRequestHeaderException("X-Parity",
                new MethodParameter(ThreeLineWebParityTest.class.getDeclaredMethod("headerEndpoint", String.class), 0));
        for (boolean enabled : new boolean[]{false, true}) {
            properties.setEnabled(enabled);
            ApiRestResponse<String> response = handler.missingRequestHeaderException(exception);
            assertEquals(400, response.getCode());
            assertEquals(enabled ? "localized header message" : "缺少请求头: [X-Parity].", response.getMessage());
        }
    }

    private void headerEndpoint(String value) { }

    private static String[] includedPatterns(MappedInterceptor interceptor) throws Exception {
        // Spring 7 移除了旧 getter，测试观察同一注册结果。
        try {
            return (String[]) MappedInterceptor.class.getMethod("getIncludePathPatterns").invoke(interceptor);
        } catch (NoSuchMethodException exception) {
            return (String[]) MappedInterceptor.class.getMethod("getPathPatterns").invoke(interceptor);
        }
    }

    interface HeaderClient {
        @RequestLine("GET /")
        @FeignHeader(autoFillTenantId = false, useWebRequestHeader = false)
        void request();
    }

    private static class RecordingRegistry extends InterceptorRegistry {
        List<Object> entries() { return getInterceptors(); }
    }
}
