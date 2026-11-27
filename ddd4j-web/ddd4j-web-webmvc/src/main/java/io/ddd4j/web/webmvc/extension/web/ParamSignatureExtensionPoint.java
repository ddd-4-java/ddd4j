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

import jakarta.servlet.http.HttpServletRequest;
import org.pf4j.ExtensionPoint;
import org.pf4j.PluginRuntimeException;

import java.util.Map;

/**
 * 参数签名扩展点（Spring Web 适配）。
 *
 * <p>本扩展点位于 ddd4j-web-webmvc 模块，
 * 因为它使用 Servlet API，属于 Web 适配层职责。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface ParamSignatureExtensionPoint extends ExtensionPoint {

    /**
     * \u8ba1\u7b97\u8bf7\u6c42\u53c2\u6570\u7b7e\u540d\u3002
     * @param request HTTP \u8bf7\u6c42
     * @param params \u8bf7\u6c42\u53c2\u6570
     * @throws PluginRuntimeException \u63d2\u4ef6\u5904\u7406\u5931\u8d25\u65f6\u629b\u51fa
     */
    void sign(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * \u67e5\u8be2\u5e94\u7528\u5bc6\u94a5\u6807\u8bc6\u3002
     * @param appid \u5e94\u7528\u6807\u8bc6
     * @return \u5e94\u7528\u5bc6\u94a5\u6807\u8bc6
     */
    String getAppkey(String appid);

    /**
     * \u67e5\u8be2\u5e94\u7528\u5bc6\u94a5\u3002
     * @param appid \u5e94\u7528\u6807\u8bc6
     * @return \u5e94\u7528\u5bc6\u94a5
     */
    String getAppSecret(String appid);

}
