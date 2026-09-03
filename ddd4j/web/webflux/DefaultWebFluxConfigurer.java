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
package io.ddd4j.web.webflux;

import io.ddd4j.web.webflux.config.LocalResourceProperteis;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.resource.LiteWebJarsResourceResolver;

import java.io.File;
import java.util.Iterator;
import java.util.Map.Entry;

public class DefaultWebFluxConfigurer implements WebFluxConfigurer {

    private final String META_INF_RESOURCES = "classpath:/META-INF/resources/";
    private final String META_INF_WEBJAR_RESOURCES = META_INF_RESOURCES + "webjars/";

    private LocalResourceProperteis localResourceProperteis;

    public DefaultWebFluxConfigurer(LocalResourceProperteis localResourceProperteis) {
        this.localResourceProperteis = localResourceProperteis;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 本地资源映射
        if (!CollectionUtils.isEmpty(localResourceProperteis.getLocalLocations())) {
            Iterator<Entry<String, String>> ite = localResourceProperteis.getLocalLocations().entrySet().iterator();
            while (ite.hasNext()) {
                Entry<String, String> entry = ite.next();
                if (localResourceProperteis.isLocalRelative()) {
                    registry.addResourceHandler(entry.getKey()).addResourceLocations(ResourceUtils.FILE_URL_PREFIX
                            + localResourceProperteis.getLocalStorage() + File.separator + entry.getValue());
                } else {
                    registry.addResourceHandler(entry.getKey()).addResourceLocations(entry.getValue());
                }
            }
        }
        // 指定个性化资源映射
        registry.addResourceHandler("/assets/**").addResourceLocations(ResourceUtils.CLASSPATH_URL_PREFIX + "/static/assets/");
        registry.addResourceHandler("/js/**").addResourceLocations(ResourceUtils.CLASSPATH_URL_PREFIX + "/static/js/");
        registry.addResourceHandler("/css/**").addResourceLocations(ResourceUtils.CLASSPATH_URL_PREFIX + "/static/css/");
        registry.addResourceHandler("/images/**").addResourceLocations(ResourceUtils.CLASSPATH_URL_PREFIX + "/static/images/");
        if (!registry.hasMappingForPattern("/webjars/**")) {
            registry.addResourceHandler("/webjars/**").addResourceLocations(META_INF_WEBJAR_RESOURCES)
                    .resourceChain(false).addResolver(new LiteWebJarsResourceResolver());
        }

    }

}
