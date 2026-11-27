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
package io.ddd4j.web.core.context;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.core.version.ApiVersion;

import java.util.Objects;

/**
 * 将各 Web 框架采集的请求数据归一化为 ddd4j 请求上下文。
 */
public final class WebRequestContextFactory {

    /** \u4e3a\u7f3a\u5c11\u8bf7\u6c42\u6807\u8bc6\u7684\u8c03\u7528\u751f\u6210\u9ed8\u8ba4\u503c\u7684\u7ec4\u4ef6\u3002 */
    private final RequestIdGenerator requestIdGenerator;
    /** \u89e3\u6790\u539f\u751f\u8fde\u63a5\u6765\u6e90\u548c\u53ef\u4fe1\u8f6c\u53d1\u5730\u5740\u7684\u7ec4\u4ef6\u3002 */
    private final ClientIpResolver clientIpResolver;

    /** \u4f7f\u7528 UUID \u8bf7\u6c42\u6807\u8bc6\u4e0e\u8fdc\u7aef\u5730\u5740\u89e3\u6790\u5668\u521b\u5efa\u5de5\u5382\u3002 */
    public WebRequestContextFactory() {
        this(RequestIdGenerator.uuid(), ClientIpResolver.remoteAddressOnly());
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u8bf7\u6c42\u6807\u8bc6\u548c\u5ba2\u6237\u7aef IP \u89e3\u6790\u5668\u521b\u5efa\u5de5\u5382\u3002
     * @param requestIdGenerator \u8bf7\u6c42\u6807\u8bc6\u751f\u6210\u5668
     * @param clientIpResolver \u5ba2\u6237\u7aef IP \u89e3\u6790\u5668
     */
    public WebRequestContextFactory(RequestIdGenerator requestIdGenerator, ClientIpResolver clientIpResolver) {
        this.requestIdGenerator = Objects.requireNonNull(requestIdGenerator, "requestIdGenerator must not be null");
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver, "clientIpResolver must not be null");
    }

    /**
     * 归一化请求数据为不携带 API 版本的请求上下文（兼容旧调用方）。
     *
     * @param data 各 Web 框架采集的原始请求元数据（非 null）
     * @return 请求上下文，{@code apiVersion} 为 null
     */
    public WebRequestContext create(WebRequestData data) {
        return create(data, null);
    }

    /**
     * 归一化请求数据为携带解析出的 API 版本的请求上下文。
     *
     * @param data 各 Web 框架采集的原始请求元数据（非 null）
     * @param apiVersion 前置过滤链解析出的 API 版本，可为 null（未接线时）
     * @return 请求上下文
     */
    public WebRequestContext create(WebRequestData data, ApiVersion apiVersion) {
        WebRequestData requestData = Objects.requireNonNull(data, "data must not be null");
        String requestId = StrKit.isBlank(requestData.requestId())
                ? requestIdGenerator.generate() : requestData.requestId().trim();
        if (StrKit.isBlank(requestId)) {
            throw new IllegalStateException("requestIdGenerator must return a non-blank request id");
        }
        String clientIp = clientIpResolver.resolve(requestData.forwardedFor(), requestData.realIp(),
                requestData.remoteAddress());
        return new WebRequestContext(requestId, requestData.traceId(), requestData.tenantId(),
                requestData.authorization(), requestData.locale(), clientIp, requestData.method(), requestData.path(),
                apiVersion);
    }
}
