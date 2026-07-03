/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webmvc.error;

import org.apache.commons.io.FilenameUtils;
import org.springframework.biz.context.support.ResourceBasenameHandler;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.net.URL;

/**
 * 国际化资源 basename 解析器。
 * <p>从资源路径中提取 i18n bundle 的 basename，用于 {@link org.springframework.context.MessageSource} 动态加载。</p>
 */
public class I18nResourceBasenameHandler implements ResourceBasenameHandler {

    /**
     * 处理资源，提取国际化 basename。
     *
     * @param resource 资源对象
     * @return 解析后的 basename 字符串
     * @throws IOException 资源访问异常
     */
    @Override
    public String handle(Resource resource) throws IOException {

        URL url = resource.getURL();

        String filepath = url.getPath();
        return "i18n" + FilenameUtils.getFullPath(filepath).split("i18n")[1] + FilenameUtils.getBaseName(filepath);
    }

}
