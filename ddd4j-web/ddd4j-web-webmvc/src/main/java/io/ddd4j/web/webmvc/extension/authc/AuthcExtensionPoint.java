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
package io.ddd4j.web.webmvc.extension.authc;

import jakarta.servlet.http.HttpServletRequest;
import org.pf4j.ExtensionPoint;
import org.pf4j.PluginRuntimeException;

import java.util.Map;

/**
 * 认证扩展点（Spring Web 适配）。
 *
 * <p>本扩展点位于 ddd4j-web-webmvc 模块，
 * 因为它使用 Servlet API（jakarta.servlet），属于 Web 适配层职责。
 * 通用 PF4J 插件体系请使用 io.github.redacted-legacy-family:pf4j-extension。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface AuthcExtensionPoint extends ExtensionPoint {

    /**
     * 从请求中获取认证令牌。
     *
     * @param request HTTP 请求
     * @param params 请求参数
     * @return 认证令牌
     * @throws PluginRuntimeException 插件处理失败时抛出
     */
    String getToken(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * 处理认证请求头。
     *
     * @param request HTTP 请求
     * @param params 请求参数
     * @throws PluginRuntimeException 插件处理失败时抛出
     */
    void handleHeader(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * 处理认证请求。
     *
     * @param request HTTP 请求
     * @param params 请求参数
     * @throws PluginRuntimeException 插件处理失败时抛出
     */
    void handleRequest(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * 处理认证结果。
     *
     * @param res 处理结果
     * @return 处理后的结果
     * @throws PluginRuntimeException 插件处理失败时抛出
     */
    Object handleResult(Object res) throws PluginRuntimeException;

}
