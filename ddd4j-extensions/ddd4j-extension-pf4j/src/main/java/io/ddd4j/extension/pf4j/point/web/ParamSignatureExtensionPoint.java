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
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public interface ParamSignatureExtensionPoint extends ExtensionPoint {

    /**
     * 为当前请求的参数计算并写入签名。
     *
     * @param request HTTP 请求对象
     * @param params  请求参数上下文，签名结果写入其中
     * @throws PluginRuntimeException 签名过程发生不可恢复错误时抛出
     */
    void sign(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    /**
     * 查询指定应用的 appkey。
     *
     * @param appid 应用标识
     * @return 对应的 appkey；不存在时返回 {@code null}
     */
    String getAppkey(String appid);

    /**
     * 查询指定应用的 appSecret。
     *
     * @param appid 应用标识
     * @return 对应的 appSecret；不存在时返回 {@code null}
     */
    String getAppSecret(String appid);

}
