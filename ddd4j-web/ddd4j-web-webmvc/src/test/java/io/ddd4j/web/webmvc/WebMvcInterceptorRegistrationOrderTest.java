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

/**
 * 验证 <code>WebMvcInterceptorRegistrationOrder</code> 在 <code>versionRunsBeforeContextEven</code> 等场景中的行为。
 */
class WebMvcInterceptorRegistrationOrderTest {

    /**
     * 验证<code>interceptors.size()</code> 等于 <code>3</code>，<code>((MappedInterceptor) interceptors.get(0)).getInterceptor()</code> 与…为同一实例 <code>version</code>，<code>((MappedInterceptor) interceptors.get(2)).getInterceptor()</code> 与…为同一实例 <code>locale</code>。
     */
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

    /**
     * 验证<code>interceptors.size()</code> 等于 <code>2</code>，<code>((MappedInterceptor) interceptors.get(0)).getInterceptor()</code> 与…为同一实例 <code>context</code>，<code>((MappedInterceptor) interceptors.get(1)).getInterceptor()</code> 与…为同一实例 <code>locale</code>。
     */
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
     * 创建 <code>new ApiVersionWebMvcConfiguration</code> 供当前测试路径调用。
     *
     * @param interceptor 传给 <code>factory.addBean()</code> 的 <code>ApiVersionWebInterceptor</code> 输入
     * @return 当前测试使用的结果，取自 <code>new ApiVersionWebMvcConfiguration(factory.getBeanProvider(ApiVersionWebInterceptor.class))</code>
     */
    private ApiVersionWebMvcConfiguration versionConfiguration(ApiVersionWebInterceptor interceptor) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("apiVersionInterceptor", interceptor);
        return new ApiVersionWebMvcConfiguration(factory.getBeanProvider(ApiVersionWebInterceptor.class));
    }

    /**
     * 创建 <code>new ApiVersionWebInterceptor</code> 供当前测试路径调用。
     *
     * @param enabled 是否开启当前测试使用的功能
     * @return 当前测试使用的结果，取自 <code>new ApiVersionWebInterceptor(resolver, enabled)</code>
     */
    private ApiVersionWebInterceptor versionInterceptor(boolean enabled) {
        ApiVersionResolver resolver = new ApiVersionResolver(ApiVersion.parse("1.0.0"), ApiVersionMode.FALLBACK);
        return new ApiVersionWebInterceptor(resolver, enabled);
    }

    /**
     * 调用 <code>WebAccessPolicy.disabled()</code> 执行当前夹具操作。
     *
     * @return 提供给后续契约断言的 <code>Ddd4jWebMvcInterceptor</code> 结果
     */
    private Ddd4jWebMvcInterceptor contextInterceptor() {
        return new Ddd4jWebMvcInterceptor(new WebRequestContextFactory(),
                new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled()), null);
    }

    /**
     * 实现 <code>InterceptorRegistry</code>，供测试调用 <code>entries()</code> 并观察结果。
     */
    private static class RecordingRegistry extends InterceptorRegistry {
        /**
         * 通过 <code>getInterceptors()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>getInterceptors()</code>
         */
        List<Object> entries() {
            return getInterceptors();
        }
    }
}
