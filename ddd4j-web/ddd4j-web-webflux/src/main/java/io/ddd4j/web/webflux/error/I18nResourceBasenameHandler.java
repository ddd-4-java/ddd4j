/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webflux.error;

import org.apache.commons.io.FilenameUtils;
import org.springframework.extension.context.support.ResourceBasenameHandler;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.net.URL;

/**
 * \u56fd\u9645\u5316\u8d44\u6e90\u57fa\u540d\u5904\u7406\u5668\uff1a\u4ece\u8d44\u6e90\u8def\u5f84\u4e2d\u622a\u53d6 {@code i18n} \u7247\u6bb5\u5e76\u62fc\u63a5\u57fa\u540d\u3002
 * @since 2.0.x
 */
public class I18nResourceBasenameHandler implements ResourceBasenameHandler {

    /**
     * \u89e3\u6790\u56fd\u9645\u5316\u8d44\u6e90\u7684\u57fa\u540d\u3002
     * @param resource \u5f85\u89e3\u6790\u7684\u8d44\u6e90
     * @return \u5f62\u5982 {@code i18n/<\u76ee\u5f55>/<\u6587\u4ef6\u540d>} \u7684\u57fa\u540d
     * @throws IOException \u8bfb\u53d6\u8d44\u6e90 URL \u5931\u8d25\u65f6\u629b\u51fa
     */
    @Override
    public String handle(Resource resource) throws IOException {

        URL url = resource.getURL();

        String filepath = url.getPath();
        return "i18n" + FilenameUtils.getFullPath(filepath).split("i18n")[1] + FilenameUtils.getBaseName(filepath);
    }

}
