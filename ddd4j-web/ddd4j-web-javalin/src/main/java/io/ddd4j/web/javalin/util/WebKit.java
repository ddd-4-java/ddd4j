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
package io.ddd4j.web.javalin.util;

import io.ddd4j.kit.lang.StrKit;
import io.javalin.http.Context;

import java.util.Objects;

/**
 * Javalin 请求工具。
 */
public final class WebKit {

    /** \u8bfb\u53d6\u5ba2\u6237\u7aef\u5730\u5740\u65f6\u8003\u8651\u7684\u8f6c\u53d1\u8bf7\u6c42\u5934\u5019\u9009\u3002 */
    private static final String[] IP_HEADER_CANDIDATES = {
            "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED", "HTTP_X_CLUSTER_CLIENT_IP", "HTTP_CLIENT_IP", "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED", "HTTP_VIA", "REMOTE_ADDR"
    };

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>WebKit</code> \u5b9e\u4f8b\u3002 */
    private WebKit() {
    }

    /**
     * 解析请求的客户端地址。
     *
     * @param context Javalin 请求上下文
     * @return 客户端地址
     */
    public static String getClientIp(Context context) {
        String ip = null;
        for (String header : IP_HEADER_CANDIDATES) {
            ip = context.header(header);
            if (StrKit.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                break;
            }
        }
        if (StrKit.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = context.ip();
        }
        if (Objects.nonNull(ip) && ip.contains(",")) {
            return ip.split(",", 2)[0].trim();
        }
        return ip;
    }

    /**
     * 判断请求是否为 AJAX 请求。
     *
     * @param context Javalin 请求上下文
     * @return 是否为 AJAX 请求
     */
    public static boolean isAjax(Context context) {
        return "XMLHttpRequest".equalsIgnoreCase(context.header("X-Requested-With"));
    }
}
