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

import javax.servlet.http.HttpServletRequest;
import org.pf4j.ExtensionPoint;
import org.pf4j.PluginRuntimeException;

import java.util.Map;

/**
 * 请求参数签名扩展点。
 *
 * <p>插件实现该接口，为主控提供参数签名与密钥（appkey/appSecret）查询能力。
 *
 * @since 2.0.x
 */
public interface ParamSignatureExtensionPoint extends ExtensionPoint {

    /**
     * 对请求参数进行签名。
     *
     * @param request HTTP 请求
     * @param params  待签名的请求参数集合
     * @throws PluginRuntimeException 签名失败时抛出
     */
    void sign(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * 按应用 ID 查询签名使用的 appkey。
     *
     * @param appid 应用 ID
     * @return appkey
     */
    String getAppkey(String appid);

    /**
     * 按应用 ID 查询签名使用的 appSecret。
     *
     * @param appid 应用 ID
     * @return appSecret
     */
    String getAppSecret(String appid);

}
