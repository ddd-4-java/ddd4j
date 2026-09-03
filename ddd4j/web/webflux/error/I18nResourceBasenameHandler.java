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
package io.ddd4j.web.webflux.error;

import org.apache.commons.io.FilenameUtils;
import org.springframework.extension.context.support.ResourceBasenameHandler;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.net.URL;

public class I18nResourceBasenameHandler implements ResourceBasenameHandler {

    @Override
    public String handle(Resource resource) throws IOException {

        URL url = resource.getURL();

        String filepath = url.getPath();
        return "i18n" + FilenameUtils.getFullPath(filepath).split("i18n")[1] + FilenameUtils.getBaseName(filepath);
    }

}
