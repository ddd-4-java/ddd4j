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

/**
 * 默认客户端地址解析器。只有显式信任反向代理时才读取转发头。
 */
public final class DefaultClientIpResolver implements ClientIpResolver {

    /** \u662f\u5426\u5141\u8bb8\u7528\u53cd\u5411\u4ee3\u7406\u8f6c\u53d1\u5934\u89e3\u6790\u5ba2\u6237\u7aef\u5730\u5740\u3002 */
    private final boolean trustForwardedHeaders;

    /**
     * 创建客户端 IP 解析器。
     *
     * @param trustForwardedHeaders 是否信任代理转发头
     */
    public DefaultClientIpResolver(boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    /**
     * 结合代理头与远端地址解析客户端 IP。
     *
     * <p>此实现调用 <code>firstForwardedAddress(forwardedFor)</code>。</p>
     *
     * @param forwardedFor 代理转发地址
     * @param realIp 真实 IP 请求头
     * @param remoteAddress 连接远端地址
     * @return 根据执行路径返回<code>forwardedAddress</code>、<code>realIp.trim()</code>、<code>StrKit.isBlank(remoteAddress) ? null : remoteAddress.trim()</code>
     */
    @Override
    public String resolve(String forwardedFor, String realIp, String remoteAddress) {
        if (trustForwardedHeaders) {
            String forwardedAddress = firstForwardedAddress(forwardedFor);
            if (StrKit.isNotBlank(forwardedAddress)) {
                return forwardedAddress;
            }
            if (StrKit.isNotBlank(realIp)) {
                return realIp.trim();
            }
        }
        return StrKit.isBlank(remoteAddress) ? null : remoteAddress.trim();
    }

    /**
     * \u4ece\u53ef\u4fe1\u8f6c\u53d1\u5730\u5740\u5217\u8868\u53d6\u51fa\u9996\u4e2a\u5ba2\u6237\u7aef\u5730\u5740\u3002
     * @param forwardedFor X-Forwarded-For \u4e2d\u7684\u5730\u5740\u5217\u8868
     * @return \u7b2c\u4e00\u9879\u5ba2\u6237\u7aef\u6765\u6e90\u5730\u5740\uff0c\u672a\u63d0\u4f9b\u65f6\u4e3a\u7a7a
     */
    private String firstForwardedAddress(String forwardedFor) {
        if (StrKit.isBlank(forwardedFor)) {
            return null;
        }
        int separator = forwardedFor.indexOf(',');
        String address = separator < 0 ? forwardedFor : forwardedFor.substring(0, separator);
        return StrKit.isBlank(address) ? null : address.trim();
    }
}
