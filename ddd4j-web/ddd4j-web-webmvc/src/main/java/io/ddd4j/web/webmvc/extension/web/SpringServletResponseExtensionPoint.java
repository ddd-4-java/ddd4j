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

import jakarta.servlet.http.HttpServletResponse;
import org.pf4j.ExtensionPoint;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Spring HTTP Servlet 响应扩展点。
 *
 * <p>本扩展点位于 ddd4j-web-webmvc 模块，
 * 因为它强依赖 Spring {@link ResponseEntity}，属于 Spring Web 适配层职责。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface SpringServletResponseExtensionPoint extends ExtensionPoint {

    /**
     * 解包外部响应。
     *
     * @param response HTTP 响应
     * @param realParams 原始请求参数
     * @return 解包后的响应
     */
    String dewrap(HttpServletResponse response, Map<String, Object> realParams);

    /**
     * 解包外部响应。
     *
     * @param responseEntity 响应实体
     * @param <T> 响应体类型
     * @return 解包后的响应
     */
    <T> ResponseEntity<T> dewrap(ResponseEntity<T> responseEntity);

}
