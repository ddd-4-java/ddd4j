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
package io.ddd4j.web.webmvc.extension.web;

import jakarta.servlet.http.HttpServletRequest;
import org.pf4j.ExtensionPoint;
import org.springframework.http.RequestEntity;

import java.util.Map;

/**
 * Spring HTTP Servlet 请求扩展点。
 *
 * <p>本扩展点位于 ddd4j-web-webmvc 模块，
 * 因为它强依赖 Spring {@link RequestEntity}，属于 Spring Web 适配层职责。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface SpringServletRequestExtensionPoint extends ExtensionPoint {

    /**
     * 包装外部请求。
     *
     * @param request HTTP 请求
     * @param realParams 原始请求参数
     * @return 包装后的请求
     */
    String wrap(HttpServletRequest request, Map<String, Object> realParams);

    /**
     * 包装外部请求。
     *
     * @param requestEntity 请求实体
     * @param <T> 请求体类型
     * @return 包装后的请求
     */
    <T> RequestEntity<T> wrap(RequestEntity<T> requestEntity);

}
