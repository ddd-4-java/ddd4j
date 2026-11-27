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
package io.ddd4j.web.webmvc.error;

import org.apache.commons.io.FilenameUtils;
import org.springframework.core.io.Resource;
import org.springframework.extension.context.support.ResourceBasenameHandler;

import java.io.IOException;
import java.net.URL;

/**
 * 国际化资源 basename 解析器。
 * <p>从资源路径中提取 i18n bundle 的 basename，用于 {@link org.springframework.context.MessageSource} 动态加载。</p>
 */
public class I18nResourceBasenameHandler implements ResourceBasenameHandler {

    /**
     * \u5904\u7406\u8d44\u6e90\uff0c\u63d0\u53d6\u56fd\u9645\u5316 basename\u3002
     * @param resource \u8d44\u6e90\u5bf9\u8c61
     * @return \u89e3\u6790\u540e\u7684 basename \u5b57\u7b26\u4e32
     * @throws IOException \u8d44\u6e90\u8bbf\u95ee\u5f02\u5e38
     */
    @Override
    /**
     * 处理资源，提取国际化 basename。
     *
     * @param resource 资源对象
     * @return 解析后的 basename 字符串
     * @throws IOException 资源访问异常
     */

    /**
     * 处理资源，提取国际化 basename。
     *
     * @param resource 资源对象
     * @return 解析后的 basename 字符串
     * @throws IOException 资源访问异常
     */
    public String handle(Resource resource) throws IOException {

        URL url = resource.getURL();

        String filepath = url.getPath();
        return "i18n" + FilenameUtils.getFullPath(filepath).split("i18n")[1] + FilenameUtils.getBaseName(filepath);
    }

}
