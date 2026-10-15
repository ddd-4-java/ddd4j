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
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class WebMvcInterceptorRegistrationOrderTest {

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

    private ApiVersionWebMvcConfiguration versionConfiguration(ApiVersionWebInterceptor interceptor) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("apiVersionInterceptor", interceptor);
        return new ApiVersionWebMvcConfiguration(factory.getBeanProvider(ApiVersionWebInterceptor.class));
    }

    private ApiVersionWebInterceptor versionInterceptor(boolean enabled) {
        ApiVersionResolver resolver = new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.FALLBACK);
        return new ApiVersionWebInterceptor(resolver, enabled);
    }

    private Ddd4jWebMvcInterceptor contextInterceptor() {
        return new Ddd4jWebMvcInterceptor(new WebRequestContextFactory(),
                new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()), null);
    }

    private static class RecordingRegistry extends InterceptorRegistry {
        List<Object> entries() {
            return getInterceptors();
        }
    }
}
