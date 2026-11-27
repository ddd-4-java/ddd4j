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
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginRuntimeException;

import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;

/**
 * 认证扩展点默认适配器（Spring Web 适配）。
 *
 * <p>本扩展点位于 ddd4j-web-webmvc 模块。
 * 提供 PF4J 插件体系下的认证扩展默认实现，可由插件覆盖：
 * <ul>
 *   <li>{@link #getToken}：从 Authorization Header 提取 Bearer Token</li>
 *   <li>{@link #handleHeader}：空实现（插件可覆盖做 Header 预处理）</li>
 *   <li>{@link #handleRequest}：将请求参数拷贝到 params Map</li>
 *   <li>{@link #handleResult}：原样返回结果</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class AuthcExtensionPointAdapter implements AuthcExtensionPoint {

    /** Authorization \u8bf7\u6c42\u5934\u540d\u79f0\u3002 */
    public static final String AUTHORIZATION_HEADER = "Authorization";
    /** Bearer \u4ee4\u724c\u524d\u7f00\u3002 */
    public static final String BEARER_PREFIX = "Bearer ";

    /**
     * 从请求中获取认证令牌。
     *
     * <p>此实现调用 <code>request.getHeader(AUTHORIZATION_HEADER)</code>。</p>
     *
     * @param request HTTP 请求
     * @param params 请求参数
     * @return 根据执行路径返回<code>null</code>、<code>authHeader.substring(BEARER_PREFIX.length()).trim()</code>、<code>tokenParam.trim()</code>
     * @throws PluginRuntimeException 当前操作无法正常完成时
     */
    @Override
    public String getToken(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException {
        if (Objects.isNull(request)) {
            return null;
        }
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (Objects.nonNull(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }
        String tokenParam = request.getParameter("token");
        if (Objects.nonNull(tokenParam) && org.springframework.util.StringUtils.hasText(tokenParam)) {
            return tokenParam.trim();
        }
        return null;
    }

    /**
     * 处理认证请求头。
     *
     * <p>此实现调用 <code>request.getHeaderNames()</code>。</p>
     *
     * @param request HTTP 请求
     * @param params 请求参数
     * @throws PluginRuntimeException 当前操作无法正常完成时
     */
    @Override
    public void handleHeader(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException {
        if (Objects.isNull(request) || Objects.isNull(params)) {
            return;
        }
        Enumeration<String> headerNames = request.getHeaderNames();
        while (Objects.nonNull(headerNames) && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (Objects.nonNull(name) && name.toLowerCase().startsWith("x-")) {
                params.putIfAbsent(name, request.getHeader(name));
            }
        }
    }

    /**
     * 处理认证请求。
     *
     * <p>此实现调用 <code>request.getParameterMap()</code>。</p>
     *
     * @param request HTTP 请求
     * @param params 请求参数
     * @throws PluginRuntimeException 当前操作无法正常完成时
     */
    @Override
    public void handleRequest(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException {
        if (Objects.isNull(request) || Objects.isNull(params)) {
            return;
        }
        Map<String, String[]> paramMap = request.getParameterMap();
        if (Objects.nonNull(paramMap)) {
            for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
                String[] values = entry.getValue();
                if (Objects.nonNull(values) && values.length > 0) {
                    params.putIfAbsent(entry.getKey(), values.length == 1 ? values[0] : values);
                }
            }
        }
    }

    /**
     * 处理认证结果。
     *
     * @param res 处理结果
     * @return 实际返回<code>res</code>，供调用方取得当前值
     * @throws PluginRuntimeException 当前操作无法正常完成时
     */
    @Override
    public Object handleResult(Object res) throws PluginRuntimeException {
        return res;
    }

}
