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
package io.ddd4j.extension.pf4j.exception;

import org.pf4j.PluginRuntimeException;

/**
 * 插件（或插件扩展点）调用失败时抛出的运行时异常。
 *
 * <p>携带出错的插件 ID 与扩展点 ID，便于定位失败来源。
 *
 * @since 2.0.x
 */
@SuppressWarnings("serial")
public class PluginInvokeException extends PluginRuntimeException {

    private String pluginId;

    private String extensionId;

    /**
     * 构造插件级调用异常。
     *
     * @param pluginId 出错的插件 ID
     * @param cause    底层异常
     */
    public PluginInvokeException(String pluginId, Throwable cause) {
        super("Plugin '" + pluginId + "' invoke error.", cause);
        this.pluginId = pluginId;
    }

    /**
     * 构造扩展点级调用异常。
     *
     * @param pluginId    出错的插件 ID
     * @param extensionId 出错的扩展点 ID
     * @param cause       底层异常
     */
    public PluginInvokeException(String pluginId, String extensionId, Throwable cause) {
        super("Plugin '" + pluginId + "' extensionId '" + extensionId + "' invoke error.", cause);
        this.pluginId = pluginId;
        this.extensionId = extensionId;
    }

    /**
     * 获取出错的插件 ID。
     *
     * @return 插件 ID
     */
    public String getPluginId() {
        return pluginId;
    }

    /**
     * 获取出错的扩展点 ID。
     *
     * @return 扩展点 ID；插件级异常时为 {@code null}
     */
    public String getExtensionId() {
        return extensionId;
    }

}
