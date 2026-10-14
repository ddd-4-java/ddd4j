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

    private static final String[] IP_HEADER_CANDIDATES = {
            "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED", "HTTP_X_CLUSTER_CLIENT_IP", "HTTP_CLIENT_IP", "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED", "HTTP_VIA", "REMOTE_ADDR"
    };

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
