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
package io.ddd4j.web.webmvc.util;

import javax.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

/**
 * Web 工具类。
 *
 * <p>该实现依赖 Servlet RequestContext，归属 {@code ddd4j-web-webmvc}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class WebUtils {

    private WebUtils() {
    }

    /**
     * 获取当前 HttpServletRequest
     */
    public static HttpServletRequest getHttpServletRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return Objects.nonNull(attrs) ? attrs.getRequest() : null;
    }

    /**
     * 获取客户端真实 IP
     */
    public static String getRemoteAddr(HttpServletRequest request) {
        if (Objects.isNull(request)) {
            return null;
        }
        String addr = request.getHeader("X-Forwarded-For");
        if (Objects.nonNull(addr) && StringUtils.hasLength(addr) && !"unknown".equalsIgnoreCase(addr)) {
            int index = addr.indexOf(',');
            return index > 0 ? addr.substring(0, index).trim() : addr.trim();
        }
        addr = request.getHeader("X-Real-IP");
        if (Objects.nonNull(addr) && StringUtils.hasLength(addr) && !"unknown".equalsIgnoreCase(addr)) {
            return addr;
        }
        return request.getRemoteAddr();
    }

}
