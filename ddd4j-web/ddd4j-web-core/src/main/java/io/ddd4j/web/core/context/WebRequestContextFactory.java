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

    private final RequestIdGenerator requestIdGenerator;
    private final ClientIpResolver clientIpResolver;

    /** 使用 UUID 请求标识与远端地址解析器创建工厂。 */
    public WebRequestContextFactory() {
        this(RequestIdGenerator.uuid(), ClientIpResolver.remoteAddressOnly());
    }

    /**
     * 使用指定请求标识和客户端 IP 解析器创建工厂。
     *
     * @param requestIdGenerator 请求标识生成器
     * @param clientIpResolver 客户端 IP 解析器
     */
    public WebRequestContextFactory(RequestIdGenerator requestIdGenerator, ClientIpResolver clientIpResolver) {
        this.requestIdGenerator = Objects.requireNonNull(requestIdGenerator, "requestIdGenerator must not be null");
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver, "clientIpResolver must not be null");
    }

    /**
     * 将原始请求数据规范化为请求上下文。
     *
     * @param data 原始请求数据
     * @return 规范化后的请求上下文
     */
    public WebRequestContext create(WebRequestData data) {
        return create(data, null);
    }

    /**
     * 将原始请求数据及已解析的 API 版本规范化为请求上下文。
     *
     * @param data 原始请求数据
     * @param apiVersion 已解析的 API 版本，可为空
     * @return 规范化后的请求上下文
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
                requestData.authorization(), requestData.locale(), clientIp, requestData.method(), requestData.path(), apiVersion);
    }
}
