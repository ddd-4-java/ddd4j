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

import io.ddd4j.core.constant.XHeaders;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.version.ApiVersion;
import io.ddd4j.web.core.version.ApiVersionMode;
import io.ddd4j.web.core.version.ApiVersionRejectedException;
import io.ddd4j.web.core.version.ApiVersionResolver;
import io.ddd4j.web.webmvc.interceptor.ApiVersionWebInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ApiVersionWebInterceptor} 单元测试与 WebMVC 运行时接线集成测试。
 *
 * <p>对应规格 Scenario（版本上下文注入）：WebMVC 注入业务处理器可读 API 版本、未接线零影响；
 * 以及 API 版本解析的非法值拒绝在 MVC 链路上的 4xx 表现。
 */
class ApiVersionWebInterceptorTest {

    private final ApiVersionResolver fallback =
            new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.FALLBACK);

    private final ApiVersionResolver strict =
            new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.STRICT);

    @AfterEach
    void clearContext() {
        ThreadContext.clear();
    }

    private MockHttpServletRequest getRequest(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    // ------------------------------ 拦截器单元测试 ------------------------------

    @Test
    void preHandleResolvesHeaderVersionIntoAttribute() throws Exception {
        ApiVersionWebInterceptor interceptor = new ApiVersionWebInterceptor(fallback);
        MockHttpServletRequest request = getRequest("/orders");
        request.addHeader(XHeaders.X_APP_VERSION, "2.1.0");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(ApiVersion.parse("2.1.0"), request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE));
    }

    @Test
    void preHandleResolvesPathVersionWhenHeaderAbsent() throws Exception {
        ApiVersionWebInterceptor interceptor = new ApiVersionWebInterceptor(fallback);
        MockHttpServletRequest request = getRequest("/v2/orders");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(ApiVersion.parse("2.0.0"), request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE));
    }

    @Test
    void preHandleFallsBackToDefaultVersionWhenBothMissing() throws Exception {
        ApiVersionWebInterceptor interceptor = new ApiVersionWebInterceptor(fallback);
        MockHttpServletRequest request = getRequest("/orders");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(ApiVersion.parse("1.0.0"), request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE));
    }

    @Test
    void preHandleDisabledLeavesAttributeAbsent() throws Exception {
        ApiVersionWebInterceptor interceptor = new ApiVersionWebInterceptor(fallback, false);
        MockHttpServletRequest request = getRequest("/v2/orders");
        request.addHeader(XHeaders.X_APP_VERSION, "2.1.0");

        assertFalse(interceptor.isEnabled());
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertNull(request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE));
    }

    @Test
    void preHandleStrictModeRejectsIllegalVersion() {
        ApiVersionWebInterceptor interceptor = new ApiVersionWebInterceptor(strict);
        MockHttpServletRequest request = getRequest("/orders");
        request.addHeader(XHeaders.X_APP_VERSION, "2.x");

        ApiVersionRejectedException exception = assertThrows(ApiVersionRejectedException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(400, exception.getStatus());
        assertTrue(exception.getMessage().contains("2.x"));
        assertNull(request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE));
    }

    @Test
    void interceptorOrderPrecedesContextInterceptor() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE, new ApiVersionWebInterceptor(fallback).getOrder());
    }

    // ------------------------------ MockMvc 集成测试 ------------------------------

    private Ddd4jWebMvcInterceptor contextInterceptor() {
        return new Ddd4jWebMvcInterceptor(new WebRequestContextFactory(),
                new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()), null);
    }

    private MockMvc wiredMockMvc(ApiVersionResolver resolver, boolean enabled) {
        return MockMvcBuilders.standaloneSetup(new VersionProbeController())
                .addInterceptors(new ApiVersionWebInterceptor(resolver, enabled), contextInterceptor())
                .setControllerAdvice(new Ddd4jWebMvcExceptionHandler(new DefaultWebExceptionTranslator()))
                .build();
    }

    @Test
    void handlerReadsApiVersionFromContext_whenHeaderPresent() throws Exception {
        MockMvc mockMvc = wiredMockMvc(fallback, true);

        MockHttpServletResponse response = mockMvc.perform(
                        MockMvcRequestBuilders.get("/version").header(XHeaders.X_APP_VERSION, "2.1.0"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("2.1.0", response.getContentAsString());
    }

    @Test
    void handlerReadsApiVersionFromContext_whenPathVersionPresent() throws Exception {
        MockMvc mockMvc = wiredMockMvc(fallback, true);

        MockHttpServletResponse response = mockMvc.perform(
                        MockMvcRequestBuilders.get("/v2/version"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("2.0.0", response.getContentAsString());
    }

    @Test
    void handlerReadsNullApiVersion_whenWiringDisabled() throws Exception {
        MockMvc mockMvc = wiredMockMvc(fallback, false);

        MockHttpServletResponse response = mockMvc.perform(
                        MockMvcRequestBuilders.get("/version").header(XHeaders.X_APP_VERSION, "2.1.0"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("absent", response.getContentAsString());
    }

    @Test
    void handlerReadsNullApiVersion_whenVersionInterceptorNotRegistered() throws Exception {
        //规格 Scenario：未接线（版本拦截器不注册）零影响，既有请求处理行为不变。
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new VersionProbeController())
                .addInterceptors(contextInterceptor())
                .setControllerAdvice(new Ddd4jWebMvcExceptionHandler(new DefaultWebExceptionTranslator()))
                .build();

        MockHttpServletResponse response = mockMvc.perform(
                        MockMvcRequestBuilders.get("/version").header(XHeaders.X_APP_VERSION, "2.1.0"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("absent", response.getContentAsString());
    }

    @Test
    void strictModeRejectsIllegalVersionWith400() throws Exception {
        MockMvc mockMvc = wiredMockMvc(strict, true);

        MockHttpServletResponse response = mockMvc.perform(
                        MockMvcRequestBuilders.get("/version").header(XHeaders.X_APP_VERSION, "2.x"))
                .andReturn().getResponse();

        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("2.x"));
    }

    /**
     * 版本探针控制器：从 {@link Ddd4jWebMvcInterceptor#CONTEXT_ATTRIBUTE} 读取完整请求上下文，
     * 回显解析出的 API 版本（未接线时回显 {@code absent}）。
     */
    @RestController
    static class VersionProbeController {

        @GetMapping({"/version", "/v2/version"})
        public String version(HttpServletRequest request) {
            Object attribute = request.getAttribute(Ddd4jWebMvcInterceptor.CONTEXT_ATTRIBUTE);
            if (!(attribute instanceof WebRequestContext)) {
                return "no-context";
            }
            WebRequestContext context = (WebRequestContext) attribute;
            return Objects.isNull(context.getApiVersion()) ? "absent" : context.getApiVersion().toString();
        }
    }
}
