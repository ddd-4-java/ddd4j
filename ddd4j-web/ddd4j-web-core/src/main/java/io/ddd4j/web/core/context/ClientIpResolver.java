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
     * \u4ec5\u4fe1\u4efb\u8fde\u63a5\u7684\u8fdc\u7aef\u5730\u5740\u3002
     * @return \u8fdc\u7aef\u5730\u5740\u89e3\u6790\u5668
     */
    static ClientIpResolver remoteAddressOnly() {
        return new DefaultClientIpResolver(false);
    }

    /**
     * \u4fe1\u4efb\u4ee3\u7406\u8f6c\u53d1\u5934\u7684\u5730\u5740\u89e3\u6790\u5668\u3002
     * @return \u53ef\u4fe1\u4ee3\u7406\u5730\u5740\u89e3\u6790\u5668
     */
    static ClientIpResolver trustedProxy() {
        return new DefaultClientIpResolver(true);
    }

    /**
     * \u7ed3\u5408\u4ee3\u7406\u5934\u4e0e\u8fdc\u7aef\u5730\u5740\u89e3\u6790\u5ba2\u6237\u7aef IP\u3002
     * @param forwardedFor \u4ee3\u7406\u8f6c\u53d1\u5730\u5740
     * @param realIp \u771f\u5b9e IP \u8bf7\u6c42\u5934
     * @param remoteAddress \u8fde\u63a5\u8fdc\u7aef\u5730\u5740
     * @return \u5ba2\u6237\u7aef IP \u5730\u5740
     */
    String resolve(String forwardedFor, String realIp, String remoteAddress);
}
