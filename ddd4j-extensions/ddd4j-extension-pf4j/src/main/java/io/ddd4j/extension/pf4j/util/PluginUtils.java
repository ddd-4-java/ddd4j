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
 * \u63d2\u4ef6\u6269\u5c55\u70b9\u67e5\u8be2\u5de5\u5177\u3002
 *
 *  <p>\u6309\u63d2\u4ef6 ID \u4e0e\u6269\u5c55\u70b9 ID \u4ece {@link PluginManager} \u4e2d\u5b9a\u4f4d\u6269\u5c55\u5b9e\u4f8b\u3002
 * @since 2.0.x
 */
public class PluginUtils {

    /**
     * \u6309\u63d2\u4ef6 ID \u4e0e\u6269\u5c55\u70b9 ID \u67e5\u8be2\u6307\u5b9a\u7c7b\u578b\u7684\u6269\u5c55\u5b9e\u4f8b\u3002
     * @param <T> \u6269\u5c55\u70b9\u7c7b\u578b
     * @param pluginManager \u63d2\u4ef6\u7ba1\u7406\u5668
     * @param type \u6269\u5c55\u70b9\u7c7b\u578b
     * @param pluginId \u63d2\u4ef6 ID
     * @param extensionId \u6269\u5c55\u70b9 ID\uff08{@link ExtensionMapping#id()}\uff09
     * @return \u5339\u914d\u7684\u6269\u5c55\u5b9e\u4f8b\uff0c\u672a\u5339\u914d\u65f6\u8fd4\u56de {@code null}
     * @throws PluginInvokeException \u67e5\u8be2\u8fc7\u7a0b\u53d1\u751f\u63d2\u4ef6\u8c03\u7528\u5f02\u5e38\u65f6\u629b\u51fa
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
     * \u67e5\u8be2 Servlet \u8bf7\u6c42\u5305\u88c5\u6269\u5c55\u70b9\u5b9e\u4f8b\u3002
     * @param pluginManager \u63d2\u4ef6\u7ba1\u7406\u5668
     * @param pluginId \u63d2\u4ef6 ID
     * @param extensionId \u6269\u5c55\u70b9 ID
     * @return \u5339\u914d\u7684\u6269\u5c55\u5b9e\u4f8b\uff0c\u672a\u5339\u914d\u65f6\u8fd4\u56de {@code null}
     * @throws PluginInvokeException \u67e5\u8be2\u8fc7\u7a0b\u53d1\u751f\u63d2\u4ef6\u8c03\u7528\u5f02\u5e38\u65f6\u629b\u51fa
     */
    public static ServletRequestExtensionPoint getRequestExtensionPoint(PluginManager pluginManager, String pluginId,
                                                                        String extensionId) throws PluginInvokeException {
        return getExtensionPoint(pluginManager, ServletRequestExtensionPoint.class, pluginId, extensionId);
    }

    /**
     * \u67e5\u8be2 Servlet \u54cd\u5e94\u62c6\u5305\u6269\u5c55\u70b9\u5b9e\u4f8b\u3002
     * @param pluginManager \u63d2\u4ef6\u7ba1\u7406\u5668
     * @param pluginId \u63d2\u4ef6 ID
     * @param extensionId \u6269\u5c55\u70b9 ID
     * @return \u5339\u914d\u7684\u6269\u5c55\u5b9e\u4f8b\uff0c\u672a\u5339\u914d\u65f6\u8fd4\u56de {@code null}
     * @throws PluginInvokeException \u67e5\u8be2\u8fc7\u7a0b\u53d1\u751f\u63d2\u4ef6\u8c03\u7528\u5f02\u5e38\u65f6\u629b\u51fa
     */
    public static ServletResponseExtensionPoint getResponseExtensionPoint(PluginManager pluginManager, String pluginId,
                                                                          String extensionId) throws PluginInvokeException {
        return getExtensionPoint(pluginManager, ServletResponseExtensionPoint.class, pluginId, extensionId);
    }

}
