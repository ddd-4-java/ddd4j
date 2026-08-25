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

    private final boolean trustForwardedHeaders;

    public DefaultClientIpResolver(boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

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

    private String firstForwardedAddress(String forwardedFor) {
        if (StrKit.isBlank(forwardedFor)) {
            return null;
        }
        int separator = forwardedFor.indexOf(',');
        String address = separator < 0 ? forwardedFor : forwardedFor.substring(0, separator);
        return StrKit.isBlank(address) ? null : address.trim();
    }
}
