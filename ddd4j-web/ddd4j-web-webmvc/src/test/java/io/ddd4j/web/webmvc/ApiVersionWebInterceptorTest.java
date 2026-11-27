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
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ApiVersionWebInterceptor} 单元测试与 WebMVC 运行时接线集成测试。
 *
 * <p>对应规格 Scenario（版本上下文注入）：WebMVC 注入业务处理器可读 API 版本、未接线零影响；
 * 以及 API 版本解析的非法值拒绝在 MVC 链路上的 4xx 表现。
 */
class ApiVersionWebInterceptorTest {

    /**
     * 使用默认回退模式的 API 版本解析器。
     */
    private final ApiVersionResolver fallback =
            new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.FALLBACK);

    /**
     * 使用严格版本解析模式的 API 版本解析器。
     */
    private final ApiVersionResolver strict =
            new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.STRICT);

    /**
     * 测试结束后执行 <code>ThreadContext.clear()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void clearContext() {
        ThreadContext.clear();
    }

    /**
     * 读取 <code>request</code> 供当前测试使用。
     *
     * @param uri 测试服务要访问的资源地址
     * @return 当前测试使用的结果，取自 <code>request</code>
     */
    private MockHttpServletRequest getRequest(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    // ------------------------------ 拦截器单元测试 ------------------------------

    /**
     * 验证<code>interceptor.preHandle(request, new MockHttpServletResponse(), new Object())</code>为真，<code>request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE)</code> 等于 <code>ApiVersion.parse("2.1.0")</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
     * 验证<code>interceptor.preHandle(request, new MockHttpServletResponse(), new Object())</code>为真，<code>request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE)</code> 等于 <code>ApiVersion.parse("2.0.0")</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void preHandleResolvesPathVersionWhenHeaderAbsent() throws Exception {
        ApiVersionWebInterceptor interceptor = new ApiVersionWebInterceptor(fallback);
        MockHttpServletRequest request = getRequest("/v2/orders");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(ApiVersion.parse("2.0.0"), request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE));
    }

    /**
     * 验证<code>interceptor.preHandle(request, new MockHttpServletResponse(), new Object())</code>为真，<code>request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE)</code> 等于 <code>ApiVersion.parse("1.0.0")</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void preHandleFallsBackToDefaultVersionWhenBothMissing() throws Exception {
        ApiVersionWebInterceptor interceptor = new ApiVersionWebInterceptor(fallback);
        MockHttpServletRequest request = getRequest("/orders");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(ApiVersion.parse("1.0.0"), request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE));
    }

    /**
     * 验证<code>interceptor.isEnabled()</code>为假，<code>interceptor.preHandle(request, new MockHttpServletResponse(), new Object())</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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

    /**
     * 验证<code>interceptor.preHandle(request, new MockHttpServletResponse(), new Object())</code> 抛出 <code>ApiVersionRejectedException.class</code>，<code>exception.getStatus()</code> 等于 <code>400</code>，<code>request.getAttribute(ApiVersionWebInterceptor.API_VERSION_ATTRIBUTE)</code>为空。
     */
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

    /**
     * 验证 MVC 版本拦截器以最高优先级解析请求版本。
     */
    @Test
    void interceptorOrderPrecedesContextInterceptor() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE, new ApiVersionWebInterceptor(fallback).getOrder());
    }

    // ------------------------------ MockMvc 集成测试 ------------------------------

    /**
     * 调用 <code>WebAccessPolicy.disabled()</code> 执行当前夹具操作。
     *
     * @return 提供给后续契约断言的 <code>Ddd4jWebMvcInterceptor</code> 结果
     */
    private Ddd4jWebMvcInterceptor contextInterceptor() {
        return new Ddd4jWebMvcInterceptor(new WebRequestContextFactory(),
                new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()), null);
    }

    /**
     * 调用 <code>MockMvcBuilders.standaloneSetup()</code>、<code>contextInterceptor()</code> 执行当前夹具操作。
     *
     * @param resolver 传给 <code>MockMvcBuilders.standaloneSetup()</code> 的 <code>ApiVersionResolver</code> 输入
     * @param enabled  是否开启当前测试使用的功能
     * @return 提供给后续契约断言的 <code>MockMvc</code> 结果
     */
    private MockMvc wiredMockMvc(ApiVersionResolver resolver, boolean enabled) {
        return MockMvcBuilders.standaloneSetup(new VersionProbeController())
                .addInterceptors(new ApiVersionWebInterceptor(resolver, enabled), contextInterceptor())
                .setControllerAdvice(new Ddd4jWebMvcExceptionHandler(new DefaultWebExceptionTranslator()))
                .build();
    }

    /**
     * 验证<code>response.getStatus()</code> 等于 <code>200</code>，<code>response.getContentAsString()</code> 等于 <code>"2.1.0"</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
     * 验证<code>response.getStatus()</code> 等于 <code>200</code>，<code>response.getContentAsString()</code> 等于 <code>"2.0.0"</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
     * 验证<code>response.getStatus()</code> 等于 <code>200</code>，<code>response.getContentAsString()</code> 等于 <code>"absent"</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
     * 验证<code>response.getStatus()</code> 等于 <code>200</code>，<code>response.getContentAsString()</code> 等于 <code>"absent"</code>。
     *
     * @throws Exception 装配运行测试所需的组件时失败
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
     * 验证<code>response.getStatus()</code> 等于 <code>400</code>，<code>response.getContentAsString().contains("2.x")</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
         * 按 <code>(!(attribute instanceof WebRequestContext))</code> 等实际条件选择当前用例返回的结果。
         *
         * @param request 待交给被测服务处理的请求
         * @return 根据测试条件返回的可能值，包括 <code>"no-context"</code>、<code>Objects.isNull(context.getApiVersion()) ? "absent" : context.getApiVersion().toString()</code>
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
