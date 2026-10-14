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
package io.ddd4j.extension.pf4j.point.authc;

import org.pf4j.ExtensionPoint;
import org.pf4j.PluginRuntimeException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 认证（Authentication）扩展点。
 *
 * <p>插件实现该接口，为主控提供从请求中提取令牌、加工请求头与请求体、包装返回结果的能力。
 *
 * @since 2.0.x
 */
public interface AuthcExtensionPoint extends ExtensionPoint {

    /**
     * 从 HTTP 请求中提取访问令牌。
     *
     * @param request HTTP 请求
     * @param params  请求参数集合
     * @return 访问令牌
     * @throws PluginRuntimeException 提取失败时抛出
     */
    String getToken(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * 加工 HTTP 请求头（如追加认证头）。
     *
     * @param request HTTP 请求
     * @param params  请求参数集合
     * @throws PluginRuntimeException 加工失败时抛出
     */
    void handleHeader(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * 加工 HTTP 请求体参数。
     *
     * @param request HTTP 请求
     * @param params  请求参数集合
     * @throws PluginRuntimeException 加工失败时抛出
     */
    void handleRequest(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * 包装业务返回结果。
     *
     * @param res 原始返回结果
     * @return 包装后的返回结果
     * @throws PluginRuntimeException 包装失败时抛出
     */
    Object handleResult(Object res) throws PluginRuntimeException;

}
