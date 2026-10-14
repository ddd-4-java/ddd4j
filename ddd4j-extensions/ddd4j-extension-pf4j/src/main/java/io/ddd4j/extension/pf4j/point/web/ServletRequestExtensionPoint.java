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
import org.springframework.http.RequestEntity;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Servlet 请求包装扩展点。
 *
 * <p>插件实现该接口，为主控提供对 HTTP 请求的改写（wrap）能力。
 *
 * @since 2.0.x
 */
public interface ServletRequestExtensionPoint extends ExtensionPoint {

    /**
     * 按真实业务参数改写 HTTP 请求。
     *
     * @param request    HTTP 请求
     * @param realParams 真实业务参数集合
     * @return 改写后的请求描述
     */
    String wrap(HttpServletRequest request, Map<String, Object> realParams);

    /**
     * 改写 {@link RequestEntity} 形式的请求。
     *
     * @param <T>           请求体类型
     * @param requestEntity 原始请求实体
     * @return 改写后的请求实体
     */
    <T> RequestEntity<T> wrap(RequestEntity<T> requestEntity);

}
