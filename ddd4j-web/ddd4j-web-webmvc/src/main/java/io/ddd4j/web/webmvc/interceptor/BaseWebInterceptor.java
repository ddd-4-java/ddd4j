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
package io.ddd4j.web.webmvc.interceptor;

import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 基础拦截器抽象类。
 * <p>实现了 {@link HandlerInterceptor} 和 {@link Ordered} 接口，提供默认的拦截路径配置。
 * 实现该接口后需要加载到 Spring 容器中。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public abstract class BaseWebInterceptor implements HandlerInterceptor, Ordered {

    /**
     * 获取拦截的请求路径模式。
     *
     * @return 路径模式数组，默认拦截所有路径 "/**"
     */
    public String[] pathPatterns() {
        return new String[]{"/**"};
    }

    /**
     * 获取不拦截的请求路径模式。
     *
     * @return 排除路径模式数组，默认排除 "/error"
     */
    public String[] excludePathPatterns() {
        return new String[]{"/error"};
    }
}