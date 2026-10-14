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

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public interface AuthcExtensionPoint extends ExtensionPoint {

    /**
     * 从当前请求中提取访问令牌。
     *
     * @param request HTTP 请求对象
     * @param params  请求参数上下文，可存放提取过程中补充的参数
     * @return 提取到的令牌；无法提取时返回 {@code null}
     * @throws PluginRuntimeException 提取过程发生不可恢复错误时抛出
     */
    String getToken(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * 对请求头做认证相关的预处理。
     *
     * @param request HTTP 请求对象
     * @param params  请求参数上下文，可写入从请求头拷贝的参数
     * @throws PluginRuntimeException 预处理过程发生不可恢复错误时抛出
     */
    void handleHeader(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * 对请求参数做认证相关的预处理。
     *
     * @param request HTTP 请求对象
     * @param params  请求参数上下文，可写入从请求参数拷贝的值
     * @throws PluginRuntimeException 预处理过程发生不可恢复错误时抛出
     */
    void handleRequest(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * 对业务处理结果做认证相关的后处理。
     *
     * @param res 业务处理的原始结果
     * @return 后处理后的结果
     * @throws PluginRuntimeException 后处理过程发生不可恢复错误时抛出
     */
    Object handleResult(Object res) throws PluginRuntimeException;

}
