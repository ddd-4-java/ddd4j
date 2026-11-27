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
package io.ddd4j.web.webmvc;

import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.version.ApiVersion;
import io.ddd4j.web.core.version.ApiVersionMode;
import io.ddd4j.web.core.version.ApiVersionResolver;
import io.ddd4j.web.webmvc.interceptor.ApiVersionWebInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** \u9a8c\u8bc1 <code>WebMvcInterceptorRegistrationOrder</code> \u5728 <code>versionRunsBeforeContextEven</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class WebMvcInterceptorRegistrationOrderTest {

    /** \u9a8c\u8bc1<code>interceptors.size()</code> \u7b49\u4e8e <code>3</code>\uff0c<code>((MappedInterceptor) interceptors.get(0)).getInterceptor()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>version</code>\uff0c<code>((MappedInterceptor) interceptors.get(2)).getInterceptor()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>locale</code>\u3002 */
    @Test
    void versionRunsBeforeContextEvenWhenRegisteredLater() {
        ApiVersionWebInterceptor version = versionInterceptor(true);
        Ddd4jWebMvcInterceptor context = contextInterceptor();
        LocaleChangeInterceptor locale = new LocaleChangeInterceptor();
        RecordingRegistry registry = new RecordingRegistry();

        new DefaultWebMvcConfigurer(null, locale, context).addInterceptors(registry);
        versionConfiguration(version).addInterceptors(registry);

        List<Object> interceptors = registry.entries();
        assertEquals(3, interceptors.size());
        assertSame(version, ((MappedInterceptor) interceptors.get(0)).getInterceptor());
        assertSame(context, ((MappedInterceptor) interceptors.get(1)).getInterceptor());
        assertSame(locale, ((MappedInterceptor) interceptors.get(2)).getInterceptor());
    }

    /** \u9a8c\u8bc1<code>interceptors.size()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>((MappedInterceptor) interceptors.get(0)).getInterceptor()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>context</code>\uff0c<code>((MappedInterceptor) interceptors.get(1)).getInterceptor()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>locale</code>\u3002 */
    @Test
    void disabledVersionLeavesContextBeforeLocale() {
        ApiVersionWebInterceptor version = versionInterceptor(false);
        Ddd4jWebMvcInterceptor context = contextInterceptor();
        LocaleChangeInterceptor locale = new LocaleChangeInterceptor();
        RecordingRegistry registry = new RecordingRegistry();

        versionConfiguration(version).addInterceptors(registry);
        new DefaultWebMvcConfigurer(null, locale, context).addInterceptors(registry);

        List<Object> interceptors = registry.entries();
        assertEquals(2, interceptors.size());
        assertSame(context, ((MappedInterceptor) interceptors.get(0)).getInterceptor());
        assertSame(locale, ((MappedInterceptor) interceptors.get(1)).getInterceptor());
    }

    /**
     * \u521b\u5efa <code>new ApiVersionWebMvcConfiguration</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param interceptor \u4f20\u7ed9 <code>factory.addBean()</code> \u7684 <code>ApiVersionWebInterceptor</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new ApiVersionWebMvcConfiguration(factory.getBeanProvider(ApiVersionWebInterceptor.class))</code>
     */
    private ApiVersionWebMvcConfiguration versionConfiguration(ApiVersionWebInterceptor interceptor) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("apiVersionInterceptor", interceptor);
        return new ApiVersionWebMvcConfiguration(factory.getBeanProvider(ApiVersionWebInterceptor.class));
    }

    /**
     * \u521b\u5efa <code>new ApiVersionWebInterceptor</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param enabled \u662f\u5426\u5f00\u542f\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u529f\u80fd
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new ApiVersionWebInterceptor(resolver, enabled)</code>
     */
    private ApiVersionWebInterceptor versionInterceptor(boolean enabled) {
        ApiVersionResolver resolver = new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.FALLBACK);
        return new ApiVersionWebInterceptor(resolver, enabled);
    }

    /**
     * \u8c03\u7528 <code>WebAccessPolicy.disabled()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>Ddd4jWebMvcInterceptor</code> \u7ed3\u679c
     */
    private Ddd4jWebMvcInterceptor contextInterceptor() {
        return new Ddd4jWebMvcInterceptor(new WebRequestContextFactory(),
                new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()), null);
    }

    /** \u5b9e\u73b0 <code>InterceptorRegistry</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>entries()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static class RecordingRegistry extends InterceptorRegistry {
        /**
         * \u901a\u8fc7 <code>getInterceptors()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>getInterceptors()</code>
         */
        List<Object> entries() {
            return getInterceptors();
        }
    }
}
