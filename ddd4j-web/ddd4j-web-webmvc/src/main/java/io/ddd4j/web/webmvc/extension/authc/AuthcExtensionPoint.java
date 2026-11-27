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
     * \u4ece\u8bf7\u6c42\u4e2d\u83b7\u53d6\u8ba4\u8bc1\u4ee4\u724c\u3002
     * @param request HTTP \u8bf7\u6c42
     * @param params \u8bf7\u6c42\u53c2\u6570
     * @return \u8ba4\u8bc1\u4ee4\u724c
     * @throws PluginRuntimeException \u63d2\u4ef6\u5904\u7406\u5931\u8d25\u65f6\u629b\u51fa
     */
    String getToken(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * \u5904\u7406\u8ba4\u8bc1\u8bf7\u6c42\u5934\u3002
     * @param request HTTP \u8bf7\u6c42
     * @param params \u8bf7\u6c42\u53c2\u6570
     * @throws PluginRuntimeException \u63d2\u4ef6\u5904\u7406\u5931\u8d25\u65f6\u629b\u51fa
     */
    void handleHeader(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * \u5904\u7406\u8ba4\u8bc1\u8bf7\u6c42\u3002
     * @param request HTTP \u8bf7\u6c42
     * @param params \u8bf7\u6c42\u53c2\u6570
     * @throws PluginRuntimeException \u63d2\u4ef6\u5904\u7406\u5931\u8d25\u65f6\u629b\u51fa
     */
    void handleRequest(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * \u5904\u7406\u8ba4\u8bc1\u7ed3\u679c\u3002
     * @param res \u5904\u7406\u7ed3\u679c
     * @return \u5904\u7406\u540e\u7684\u7ed3\u679c
     * @throws PluginRuntimeException \u63d2\u4ef6\u5904\u7406\u5931\u8d25\u65f6\u629b\u51fa
     */
    Object handleResult(Object res) throws PluginRuntimeException;

}
