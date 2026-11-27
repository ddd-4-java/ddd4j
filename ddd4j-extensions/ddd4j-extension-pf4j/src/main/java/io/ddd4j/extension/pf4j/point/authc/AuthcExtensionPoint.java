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
 * \u8ba4\u8bc1\uff08Authentication\uff09\u6269\u5c55\u70b9\u3002
 *
 *  <p>\u63d2\u4ef6\u5b9e\u73b0\u8be5\u63a5\u53e3\uff0c\u4e3a\u4e3b\u63a7\u63d0\u4f9b\u4ece\u8bf7\u6c42\u4e2d\u63d0\u53d6\u4ee4\u724c\u3001\u52a0\u5de5\u8bf7\u6c42\u5934\u4e0e\u8bf7\u6c42\u4f53\u3001\u5305\u88c5\u8fd4\u56de\u7ed3\u679c\u7684\u80fd\u529b\u3002
 * @since 2.0.x
 */
public interface AuthcExtensionPoint extends ExtensionPoint {

    /**
     * \u4ece HTTP \u8bf7\u6c42\u4e2d\u63d0\u53d6\u8bbf\u95ee\u4ee4\u724c\u3002
     * @param request HTTP \u8bf7\u6c42
     * @param params \u8bf7\u6c42\u53c2\u6570\u96c6\u5408
     * @return \u8bbf\u95ee\u4ee4\u724c
     * @throws PluginRuntimeException \u63d0\u53d6\u5931\u8d25\u65f6\u629b\u51fa
     */
    String getToken(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * \u52a0\u5de5 HTTP \u8bf7\u6c42\u5934\uff08\u5982\u8ffd\u52a0\u8ba4\u8bc1\u5934\uff09\u3002
     * @param request HTTP \u8bf7\u6c42
     * @param params \u8bf7\u6c42\u53c2\u6570\u96c6\u5408
     * @throws PluginRuntimeException \u52a0\u5de5\u5931\u8d25\u65f6\u629b\u51fa
     */
    void handleHeader(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * \u52a0\u5de5 HTTP \u8bf7\u6c42\u4f53\u53c2\u6570\u3002
     * @param request HTTP \u8bf7\u6c42
     * @param params \u8bf7\u6c42\u53c2\u6570\u96c6\u5408
     * @throws PluginRuntimeException \u52a0\u5de5\u5931\u8d25\u65f6\u629b\u51fa
     */
    void handleRequest(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * \u5305\u88c5\u4e1a\u52a1\u8fd4\u56de\u7ed3\u679c\u3002
     * @param res \u539f\u59cb\u8fd4\u56de\u7ed3\u679c
     * @return \u5305\u88c5\u540e\u7684\u8fd4\u56de\u7ed3\u679c
     * @throws PluginRuntimeException \u5305\u88c5\u5931\u8d25\u65f6\u629b\u51fa
     */
    Object handleResult(Object res) throws PluginRuntimeException;

}
