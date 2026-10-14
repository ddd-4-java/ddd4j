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
     * 仅信任连接的远端地址。
     *
     * @return 远端地址解析器
     */
    static ClientIpResolver remoteAddressOnly() {
        return new DefaultClientIpResolver(false);
    }

    /**
     * 信任代理转发头的地址解析器。
     *
     * @return 可信代理地址解析器
     */
    static ClientIpResolver trustedProxy() {
        return new DefaultClientIpResolver(true);
    }

    /**
     * 结合代理头与远端地址解析客户端 IP。
     *
     * @param forwardedFor 代理转发地址
     * @param realIp 真实 IP 请求头
     * @param remoteAddress 连接远端地址
     * @return 客户端 IP 地址
     */
    String resolve(String forwardedFor, String realIp, String remoteAddress);
}
