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

/**
 * 从代理头和远端地址中解析客户端地址。
 */
@FunctionalInterface
public interface ClientIpResolver {

    /**
     * 创建仅信任直连地址的解析器：忽略所有代理头。
     *
     * @return 只使用对端地址的解析器
     */
    static ClientIpResolver remoteAddressOnly() {
        return new DefaultClientIpResolver(false);
    }

    /**
     * 创建信任代理头的解析器：优先从转发头中取客户端地址。
     *
     * @return 优先使用代理头的解析器
     */
    static ClientIpResolver trustedProxy() {
        return new DefaultClientIpResolver(true);
    }

    /**
     * 解析客户端真实地址。
     *
     * @param forwardedFor 转发链路头（如 {@code X-Forwarded-For}），可为空
     * @param realIp 真实 IP 头（如 {@code X-Real-IP}），可为空
     * @param remoteAddress 连接的对端地址
     * @return 解析得到的客户端地址
     */
    String resolve(String forwardedFor, String realIp, String remoteAddress);
}
