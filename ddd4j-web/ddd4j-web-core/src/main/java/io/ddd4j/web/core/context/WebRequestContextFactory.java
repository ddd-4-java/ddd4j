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
     * create() 方法。
     * @param data data 参数。
     * @return 方法执行结果。
     */
    public WebRequestContext create(WebRequestData data) {
        return create(data, null);
    }

    /**
     * \u5c06\u539f\u59cb\u8bf7\u6c42\u6570\u636e\u53ca\u5df2\u89e3\u6790\u7684 API \u7248\u672c\u89c4\u8303\u5316\u4e3a\u8bf7\u6c42\u4e0a\u4e0b\u6587\u3002
     * @param data \u539f\u59cb\u8bf7\u6c42\u6570\u636e
     * @param apiVersion \u5df2\u89e3\u6790\u7684 API \u7248\u672c\uff0c\u53ef\u4e3a\u7a7a
     * @return \u89c4\u8303\u5316\u540e\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
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
