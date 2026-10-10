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
package io.ddd4j.extension.pf4j.util;

import io.ddd4j.extension.pf4j.annotation.ExtensionMapping;
import io.ddd4j.extension.pf4j.exception.PluginInvokeException;
import io.ddd4j.extension.pf4j.point.web.ServletRequestExtensionPoint;
import io.ddd4j.extension.pf4j.point.web.ServletResponseExtensionPoint;
import org.pf4j.PluginManager;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 插件扩展点查询工具。
 *
 * <p>按插件 ID 与扩展点 ID 从 {@link PluginManager} 中定位扩展实例。
 *
 * @since 2.0.x
 */
public class PluginUtils {

    /**
     * 按插件 ID 与扩展点 ID 查询指定类型的扩展实例。
     *
     * @param <T>          扩展点类型
     * @param pluginManager 插件管理器
     * @param type         扩展点类型
     * @param pluginId     插件 ID
     * @param extensionId  扩展点 ID（{@link ExtensionMapping#id()}）
     * @return 匹配的扩展实例，未匹配时返回 {@code null}
     * @throws PluginInvokeException 查询过程发生插件调用异常时抛出
     */
    public static <T> T getExtensionPoint(PluginManager pluginManager, Class<T> type, String pluginId,
                                          String extensionId) throws PluginInvokeException {
        if (StringUtils.hasText(pluginId) && StringUtils.hasText(extensionId)) {
            List<T> extensions = pluginManager.getExtensions(type, pluginId);
            for (T extension : extensions) {
                ExtensionMapping em = extension.getClass().getAnnotation(ExtensionMapping.class);
                if (StringUtils.hasText(em.id()) && em.id().equals(extensionId)) {
                    return extension;
                }
            }
        }
        return null;
    }

    /**
     * 查询 Servlet 请求包装扩展点实例。
     *
     * @param pluginManager 插件管理器
     * @param pluginId      插件 ID
     * @param extensionId   扩展点 ID
     * @return 匹配的扩展实例，未匹配时返回 {@code null}
     * @throws PluginInvokeException 查询过程发生插件调用异常时抛出
     */
    public static ServletRequestExtensionPoint getRequestExtensionPoint(PluginManager pluginManager, String pluginId,
                                                                        String extensionId) throws PluginInvokeException {
        return getExtensionPoint(pluginManager, ServletRequestExtensionPoint.class, pluginId, extensionId);
    }

    /**
     * 查询 Servlet 响应拆包扩展点实例。
     *
     * @param pluginManager 插件管理器
     * @param pluginId      插件 ID
     * @param extensionId   扩展点 ID
     * @return 匹配的扩展实例，未匹配时返回 {@code null}
     * @throws PluginInvokeException 查询过程发生插件调用异常时抛出
     */
    public static ServletResponseExtensionPoint getResponseExtensionPoint(PluginManager pluginManager, String pluginId,
                                                                          String extensionId) throws PluginInvokeException {
        return getExtensionPoint(pluginManager, ServletResponseExtensionPoint.class, pluginId, extensionId);
    }

}
