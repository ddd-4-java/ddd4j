/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
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

/**
 * WebFlux \u9759\u6001\u8d44\u6e90\u6620\u5c04\u7684\u9ed8\u8ba4\u914d\u7f6e\u5668\u3002
 *
 *  <p>\u6ce8\u518c\u672c\u5730\u8d44\u6e90\u3001classpath \u9759\u6001\u8d44\u6e90\u4e0e WebJars \u8d44\u6e90\u5904\u7406\u5668\u3002
 */
public class DefaultWebFluxConfigurer implements WebFluxConfigurer {

    /** \u7ea6\u5b9a\u9759\u6001 Web \u8d44\u6e90\u7684 classpath \u4f4d\u7f6e\u3002 */
    private final String META_INF_RESOURCES = "classpath:/META-INF/resources/";
    /** WebJars \u9759\u6001\u8d44\u6e90\u7684 classpath \u4f4d\u7f6e\u3002 */
    private final String META_INF_WEBJAR_RESOURCES = META_INF_RESOURCES + "webjars/";

    /** \u914d\u7f6e\u672c\u5730\u76ee\u5f55\u548c\u9759\u6001\u8d44\u6e90\u8bbf\u95ee\u8def\u5f84\u7684\u5c5e\u6027\u3002 */
    private LocalResourceProperteis localResourceProperteis;

    /**
     * \u6784\u9020\u8d44\u6e90\u914d\u7f6e\u5668\u3002
     * @param localResourceProperteis \u672c\u5730\u9759\u6001\u8d44\u6e90\u6620\u5c04\u914d\u7f6e
     */
    public DefaultWebFluxConfigurer(LocalResourceProperteis localResourceProperteis) {
        this.localResourceProperteis = localResourceProperteis;
    }

    /**
     * \u6ce8\u518c\u9759\u6001\u8d44\u6e90\u5904\u7406\u5668\uff1a\u672c\u5730\u8d44\u6e90\u3001{@code /assets|/js|/css|/images} \u4e0e WebJars\u3002
     * @param registry WebFlux \u8d44\u6e90\u5904\u7406\u5668\u6ce8\u518c\u8868
     */
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
