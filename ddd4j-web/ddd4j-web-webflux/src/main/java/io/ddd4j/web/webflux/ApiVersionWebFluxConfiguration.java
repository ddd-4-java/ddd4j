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

import io.ddd4j.web.core.version.ApiVersion;
import io.ddd4j.web.core.version.ApiVersionMode;
import io.ddd4j.web.core.version.ApiVersionResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * API 版本解析在 Spring WebFlux 的注册配置。
 *
 * <p><b>注册说明</b>：本类为普通 {@code @Configuration}（本仓库不带
 * {@code spring-boot-autoconfigure}，不使用条件装配注解），加入组件扫描或显式 {@code @Import}
 * 即生效；{@link ApiVersionWebFilter} 作为 WebFilter Bean 由 WebFlux 过滤链自动收集并按
 * {@code Ordered} 排序（{@link ApiVersionWebFilter#getOrder()} 为 {@code HIGHEST_PRECEDENCE}，
 * 先于上下文过滤器 {@code Ddd4jWebFluxFilter} 执行），无需额外注册步骤。
 *
 * <p><b>可配置项</b>（{@code ddd4j.web.api-version.*}，与 WebMVC 侧同一组属性）：
 * <ul>
 *   <li>{@code enabled}（默认 {@code true}）：为 {@code false} 时不过滤任何请求，
 *       请求处理行为与接线前完全一致（零影响）；</li>
 *   <li>{@code default-version}（默认 {@code 1.0.0}）：头与路径双缺失时的兜底版本；</li>
 *   <li>{@code mode}（{@code fallback}|{@code strict}，默认 {@code fallback}）：
 *       非法版本值的处理模式。</li>
 * </ul>
 *
 * @see ApiVersionWebFilter
 * @see io.ddd4j.web.webflux.Ddd4jWebFluxFilter
 */
@Configuration(proxyBeanMethods = false)
public class ApiVersionWebFluxConfiguration {

    /** 配置属性前缀（与 WebMVC 侧共享）。 */
    public static final String PROPERTY_PREFIX = "ddd4j.web.api-version";

    /** 属性名：是否启用版本解析。 */
    public static final String PROPERTY_ENABLED = PROPERTY_PREFIX + ".enabled";

    /** 属性名：默认版本。 */
    public static final String PROPERTY_DEFAULT_VERSION = PROPERTY_PREFIX + ".default-version";

    /** 属性名：非法版本值处理模式。 */
    public static final String PROPERTY_MODE = PROPERTY_PREFIX + ".mode";

    /**
     * 版本解析器 Bean：读取默认版本与模式属性构建。
     *
     * @param environment Spring 环境，用于读取 {@code ddd4j.web.api-version.*} 属性
     * @return 版本解析器
     * @throws IllegalArgumentException 默认版本属性非法时由 {@link ApiVersion#parse(String)} 抛出
     */
    @Bean
    public ApiVersionResolver apiVersionResolver(Environment environment) {
        String defaultVersion = environment.getProperty(PROPERTY_DEFAULT_VERSION, "1.0.0");
        String mode = environment.getProperty(PROPERTY_MODE, ApiVersionMode.FALLBACK.name());
        return new ApiVersionResolver(ApiVersion.parse(defaultVersion), resolveMode(mode));
    }

    /**
     * API 版本解析过滤器 Bean：按 {@code enabled} 属性携带启用状态；
     * 关闭时 {@code filter()} 完全旁路，等价于未注册。
     *
     * @param resolver 版本解析器
     * @param environment Spring 环境
     * @return 版本过滤器
     */
    @Bean
    public ApiVersionWebFilter apiVersionWebFilter(ApiVersionResolver resolver, Environment environment) {
        boolean enabled = environment.getProperty(PROPERTY_ENABLED, Boolean.class, Boolean.TRUE);
        return new ApiVersionWebFilter(resolver, enabled);
    }

    /**
     * 解析模式属性。
     *
     * @param mode 属性值，{@code strict}（忽略大小写）为严格模式，其余为回退模式
     * @return 模式枚举
     */
    private ApiVersionMode resolveMode(String mode) {
        return ApiVersionMode.STRICT.name().equalsIgnoreCase(mode) ? ApiVersionMode.STRICT : ApiVersionMode.FALLBACK;
    }
}
