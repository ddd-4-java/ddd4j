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

    /** \u4f7f\u7528\u9ed8\u8ba4\u56de\u9000\u6a21\u5f0f\u7684 API \u7248\u672c\u89e3\u6790\u5668\u3002 */
    private final ApiVersionResolver fallback =
            new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.FALLBACK);

    /** \u4f7f\u7528\u4e25\u683c\u7248\u672c\u89e3\u6790\u6a21\u5f0f\u7684 API \u7248\u672c\u89e3\u6790\u5668\u3002 */
    private final ApiVersionResolver strict =
            new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.STRICT);

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>ThreadContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        ThreadContext.clear();
    }

    /**
     * \u8bfb\u53d6 <code>request</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param uri \u6d4b\u8bd5\u670d\u52a1\u8981\u8bbf\u95ee\u7684\u8d44\u6e90\u5730\u5740
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>request</code>
     */
    private MockHttpServletRequest getRequest(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    // ------------------------------ 拦截器单元测试 ------------------------------

    /**
     * \u9a8c\u8bc1<code>interceptor.preHandle(request, new MockHttpServletResponse(), new Object())</code>\u4e3a\u771f\uff0c<code>request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE)</code> \u7b49\u4e8e <code>ApiVersion.parse("2.1.0")</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void preHandleResolvesHeaderVersionIntoAttribute() throws Exception {
        ApiVersionWebInterceptor interceptor = new ApiVersionWebInterceptor(fallback);
        MockHttpServletRequest request = getRequest("/orders");
        request.addHeader(XHeaders.X_APP_VERSION, "2.1.0");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(ApiVersion.parse("2.1.0"), request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE));
    }

    /**
     * \u9a8c\u8bc1<code>interceptor.preHandle(request, new MockHttpServletResponse(), new Object())</code>\u4e3a\u771f\uff0c<code>request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE)</code> \u7b49\u4e8e <code>ApiVersion.parse("2.0.0")</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void preHandleResolvesPathVersionWhenHeaderAbsent() throws Exception {
        ApiVersionWebInterceptor interceptor = new ApiVersionWebInterceptor(fallback);
        MockHttpServletRequest request = getRequest("/v2/orders");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(ApiVersion.parse("2.0.0"), request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE));
    }

    /**
     * \u9a8c\u8bc1<code>interceptor.preHandle(request, new MockHttpServletResponse(), new Object())</code>\u4e3a\u771f\uff0c<code>request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE)</code> \u7b49\u4e8e <code>ApiVersion.parse("1.0.0")</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void preHandleFallsBackToDefaultVersionWhenBothMissing() throws Exception {
        ApiVersionWebInterceptor interceptor = new ApiVersionWebInterceptor(fallback);
        MockHttpServletRequest request = getRequest("/orders");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(ApiVersion.parse("1.0.0"), request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE));
    }

    /**
     * \u9a8c\u8bc1<code>interceptor.isEnabled()</code>\u4e3a\u5047\uff0c<code>interceptor.preHandle(request, new MockHttpServletResponse(), new Object())</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void preHandleDisabledLeavesAttributeAbsent() throws Exception {
        ApiVersionWebInterceptor interceptor = new ApiVersionWebInterceptor(fallback, false);
        MockHttpServletRequest request = getRequest("/v2/orders");
        request.addHeader(XHeaders.X_APP_VERSION, "2.1.0");

        assertFalse(interceptor.isEnabled());
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertNull(request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE));
    }

    /** \u9a8c\u8bc1<code>interceptor.preHandle(request, new MockHttpServletResponse(), new Object())</code> \u629b\u51fa <code>ApiVersionRejectedException.class</code>\uff0c<code>exception.getStatus()</code> \u7b49\u4e8e <code>400</code>\uff0c<code>request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE)</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1 MVC \u7248\u672c\u62e6\u622a\u5668\u4ee5\u6700\u9ad8\u4f18\u5148\u7ea7\u89e3\u6790\u8bf7\u6c42\u7248\u672c\u3002 */
    @Test
    void interceptorOrderPrecedesContextInterceptor() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE, new ApiVersionWebInterceptor(fallback).getOrder());
    }

    // ------------------------------ MockMvc 集成测试 ------------------------------

    /**
     * \u8c03\u7528 <code>WebAccessPolicy.disabled()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>Ddd4jWebMvcInterceptor</code> \u7ed3\u679c
     */
    private Ddd4jWebMvcInterceptor contextInterceptor() {
        return new Ddd4jWebMvcInterceptor(new WebRequestContextFactory(),
                new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()), null);
    }

    /**
     * \u8c03\u7528 <code>MockMvcBuilders.standaloneSetup()</code>\u3001<code>contextInterceptor()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param resolver \u4f20\u7ed9 <code>MockMvcBuilders.standaloneSetup()</code> \u7684 <code>ApiVersionResolver</code> \u8f93\u5165
     * @param enabled \u662f\u5426\u5f00\u542f\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u529f\u80fd
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>MockMvc</code> \u7ed3\u679c
     */
    private MockMvc wiredMockMvc(ApiVersionResolver resolver, boolean enabled) {
        return MockMvcBuilders.standaloneSetup(new VersionProbeController())
                .addInterceptors(new ApiVersionWebInterceptor(resolver, enabled), contextInterceptor())
                .setControllerAdvice(new Ddd4jWebMvcExceptionHandler(new DefaultWebExceptionTranslator()))
                .build();
    }

    /**
     * \u9a8c\u8bc1<code>response.getStatus()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>response.getContentAsString()</code> \u7b49\u4e8e <code>"2.1.0"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void handlerReadsApiVersionFromContext_whenHeaderPresent() throws Exception {
        MockMvc mockMvc = wiredMockMvc(fallback, true);

        MockHttpServletResponse response = mockMvc.perform(
                        MockMvcRequestBuilders.get("/version").header(XHeaders.X_APP_VERSION, "2.1.0"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("2.1.0", response.getContentAsString());
    }

    /**
     * \u9a8c\u8bc1<code>response.getStatus()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>response.getContentAsString()</code> \u7b49\u4e8e <code>"2.0.0"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void handlerReadsApiVersionFromContext_whenPathVersionPresent() throws Exception {
        MockMvc mockMvc = wiredMockMvc(fallback, true);

        MockHttpServletResponse response = mockMvc.perform(
                        MockMvcRequestBuilders.get("/v2/version"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("2.0.0", response.getContentAsString());
    }

    /**
     * \u9a8c\u8bc1<code>response.getStatus()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>response.getContentAsString()</code> \u7b49\u4e8e <code>"absent"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void handlerReadsNullApiVersion_whenWiringDisabled() throws Exception {
        MockMvc mockMvc = wiredMockMvc(fallback, false);

        MockHttpServletResponse response = mockMvc.perform(
                        MockMvcRequestBuilders.get("/version").header(XHeaders.X_APP_VERSION, "2.1.0"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("absent", response.getContentAsString());
    }

    /**
     * \u9a8c\u8bc1<code>response.getStatus()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>response.getContentAsString()</code> \u7b49\u4e8e <code>"absent"</code>\u3002
     * @throws Exception \u88c5\u914d\u8fd0\u884c\u6d4b\u8bd5\u6240\u9700\u7684\u7ec4\u4ef6\u65f6\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>response.getStatus()</code> \u7b49\u4e8e <code>400</code>\uff0c<code>response.getContentAsString().contains("2.x")</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
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

        /**
         * \u6309 <code>(!(attribute instanceof WebRequestContext))</code> \u7b49\u5b9e\u9645\u6761\u4ef6\u9009\u62e9\u5f53\u524d\u7528\u4f8b\u8fd4\u56de\u7684\u7ed3\u679c\u3002
         * @param request \u5f85\u4ea4\u7ed9\u88ab\u6d4b\u670d\u52a1\u5904\u7406\u7684\u8bf7\u6c42
         * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>"no-context"</code>\u3001<code>Objects.isNull(context.getApiVersion()) ? "absent" : context.getApiVersion().toString()</code>
         */
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
