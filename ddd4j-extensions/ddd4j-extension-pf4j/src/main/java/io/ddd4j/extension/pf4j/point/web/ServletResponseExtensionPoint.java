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
package io.ddd4j.extension.pf4j.point.web;

import org.pf4j.ExtensionPoint;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Servlet 响应拆包扩展点。
 *
 * <p>插件实现该接口，为主控提供对 HTTP 响应的还原（dewrap）能力。
 *
 * @since 2.0.x
 */
public interface ServletResponseExtensionPoint extends ExtensionPoint {

    /**
     * 按真实业务参数还原 HTTP 响应。
     *
     * @param response   HTTP 响应
     * @param realParams 真实业务参数集合
     * @return 还原后的响应描述
     */
    String dewrap(HttpServletResponse response, Map<String, Object> realParams);

    /**
     * 还原 {@link ResponseEntity} 形式的响应。
     *
     * @param <T>            响应体类型
     * @param responseEntity 原始响应实体
     * @return 还原后的响应实体
     */
    <T> ResponseEntity<T> dewrap(ResponseEntity<T> responseEntity);

}
