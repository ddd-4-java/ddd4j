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
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public class PluginUtils {

    /**
     * 获取值。
     * @param <T> 类型参数。
     * @param pluginManager pluginManager 参数。
     * @param type 目标类型。
     * @param pluginId 字符串参数。
     * @param extensionId 字符串参数。
     * @return 结果值。
     * @throws PluginInvokeException 出现异常时抛出。
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
     * 获取值。
     * @param pluginManager pluginManager 参数。
     * @param pluginId 字符串参数。
     * @param extensionId 字符串参数。
     * @return 方法执行结果。
     * @throws PluginInvokeException 出现异常时抛出。
     */
    public static ServletRequestExtensionPoint getRequestExtensionPoint(PluginManager pluginManager, String pluginId,
                                                                        String extensionId) throws PluginInvokeException {
        return getExtensionPoint(pluginManager, ServletRequestExtensionPoint.class, pluginId, extensionId);
    }

    /**
     * 获取值。
     * @param pluginManager pluginManager 参数。
     * @param pluginId 字符串参数。
     * @param extensionId 字符串参数。
     * @return 方法执行结果。
     * @throws PluginInvokeException 出现异常时抛出。
     */
    public static ServletResponseExtensionPoint getResponseExtensionPoint(PluginManager pluginManager, String pluginId,
                                                                          String extensionId) throws PluginInvokeException {
        return getExtensionPoint(pluginManager, ServletResponseExtensionPoint.class, pluginId, extensionId);
    }

}
