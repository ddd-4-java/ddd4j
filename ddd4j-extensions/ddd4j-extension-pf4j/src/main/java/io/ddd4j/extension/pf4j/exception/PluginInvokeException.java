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
 * \u63d2\u4ef6\uff08\u6216\u63d2\u4ef6\u6269\u5c55\u70b9\uff09\u8c03\u7528\u5931\u8d25\u65f6\u629b\u51fa\u7684\u8fd0\u884c\u65f6\u5f02\u5e38\u3002
 *
 *  <p>\u643a\u5e26\u51fa\u9519\u7684\u63d2\u4ef6 ID \u4e0e\u6269\u5c55\u70b9 ID\uff0c\u4fbf\u4e8e\u5b9a\u4f4d\u5931\u8d25\u6765\u6e90\u3002
 * @since 2.0.x
 */
@SuppressWarnings("serial")
public class PluginInvokeException extends PluginRuntimeException {

    /** \u51fa\u9519\u7684\u63d2\u4ef6 ID\u3002 */
    private String pluginId;

    /** \u51fa\u9519\u7684\u6269\u5c55\u70b9 ID\u3002 */
    private String extensionId;

    /**
     * \u6784\u9020\u63d2\u4ef6\u7ea7\u8c03\u7528\u5f02\u5e38\u3002
     * @param pluginId \u51fa\u9519\u7684\u63d2\u4ef6 ID
     * @param cause \u5e95\u5c42\u5f02\u5e38
     */
    public PluginInvokeException(String pluginId, Throwable cause) {
        super("Plugin '" + pluginId + "' invoke error.", cause);
        this.pluginId = pluginId;
    }

    /**
     * \u6784\u9020\u6269\u5c55\u70b9\u7ea7\u8c03\u7528\u5f02\u5e38\u3002
     * @param pluginId \u51fa\u9519\u7684\u63d2\u4ef6 ID
     * @param extensionId \u51fa\u9519\u7684\u6269\u5c55\u70b9 ID
     * @param cause \u5e95\u5c42\u5f02\u5e38
     */
    public PluginInvokeException(String pluginId, String extensionId, Throwable cause) {
        super("Plugin '" + pluginId + "' extensionId '" + extensionId + "' invoke error.", cause);
        this.pluginId = pluginId;
        this.extensionId = extensionId;
    }

    /**
     * \u83b7\u53d6\u51fa\u9519\u7684\u63d2\u4ef6 ID\u3002
     * @return \u63d2\u4ef6 ID
     */
    public String getPluginId() {
        return pluginId;
    }

    /**
     * \u83b7\u53d6\u51fa\u9519\u7684\u6269\u5c55\u70b9 ID\u3002
     * @return \u6269\u5c55\u70b9 ID\uff1b\u63d2\u4ef6\u7ea7\u5f02\u5e38\u65f6\u4e3a {@code null}
     */
    public String getExtensionId() {
        return extensionId;
    }

}
