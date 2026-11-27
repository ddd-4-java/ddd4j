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
import org.pf4j.PluginRuntimeException;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * \u8bf7\u6c42\u53c2\u6570\u7b7e\u540d\u6269\u5c55\u70b9\u3002
 *
 *  <p>\u63d2\u4ef6\u5b9e\u73b0\u8be5\u63a5\u53e3\uff0c\u4e3a\u4e3b\u63a7\u63d0\u4f9b\u53c2\u6570\u7b7e\u540d\u4e0e\u5bc6\u94a5\uff08appkey/appSecret\uff09\u67e5\u8be2\u80fd\u529b\u3002
 * @since 2.0.x
 */
public interface ParamSignatureExtensionPoint extends ExtensionPoint {

    /**
     * \u5bf9\u8bf7\u6c42\u53c2\u6570\u8fdb\u884c\u7b7e\u540d\u3002
     * @param request HTTP \u8bf7\u6c42
     * @param params \u5f85\u7b7e\u540d\u7684\u8bf7\u6c42\u53c2\u6570\u96c6\u5408
     * @throws PluginRuntimeException \u7b7e\u540d\u5931\u8d25\u65f6\u629b\u51fa
     */
    void sign(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * \u6309\u5e94\u7528 ID \u67e5\u8be2\u7b7e\u540d\u4f7f\u7528\u7684 appkey\u3002
     * @param appid \u5e94\u7528 ID
     * @return appkey
     */
    String getAppkey(String appid);

    /**
     * \u6309\u5e94\u7528 ID \u67e5\u8be2\u7b7e\u540d\u4f7f\u7528\u7684 appSecret\u3002
     * @param appid \u5e94\u7528 ID
     * @return appSecret
     */
    String getAppSecret(String appid);

}
