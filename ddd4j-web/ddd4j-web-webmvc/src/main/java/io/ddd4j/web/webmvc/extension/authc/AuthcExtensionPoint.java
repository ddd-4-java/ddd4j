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
 * <p>从 io.github.hiwepy:pf4j-extension 迁入至 ddd4j-web-webmvc 模块，
 * 因为它使用 Servlet API（jakarta.servlet），属于 Web 适配层职责。
 * 通用 PF4J 插件体系请使用 io.github.hiwepy:pf4j-extension。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface AuthcExtensionPoint extends ExtensionPoint {

    String getToken(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    void handleHeader(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    void handleRequest(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    Object handleResult(Object res) throws PluginRuntimeException;

}
