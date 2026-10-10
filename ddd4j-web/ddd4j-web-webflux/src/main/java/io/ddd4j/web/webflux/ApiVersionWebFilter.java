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

import io.ddd4j.core.constant.XHeaders;
import io.ddd4j.web.core.version.ApiVersion;
import io.ddd4j.web.core.version.ApiVersionResolver;
import io.ddd4j.web.core.version.ApiVersionResult;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Spring WebFlux 的 API 版本解析过滤器（与 WebMVC 拦截器同语义）。
 *
 * <p>在请求进入 {@code Ddd4jWebFluxFilter} 前按冻结优先级（{@code X-App-Version} 请求头
 * &gt; URL 路径版本段 &gt; 配置默认版本）解析客户端 API 版本，挂载到 exchange 属性
 * {@link #API_VERSION_ATTRIBUTE}；{@code Ddd4jWebFluxFilter#createContext} 读取该属性，
 * 使业务处理器可从 {@code WebRequestContext} 读到解析出的 {@link ApiVersion}。
 *
 * <p>严格模式下非法版本值由解析器抛出 {@code ApiVersionRejectedException}（4xx），
 * 在 {@code Mono.defer} 内抛出即转为 {@code Mono.error}，沿既有全局异常处理链转为拒绝响应。
 * 支持通过 {@link #isEnabled()} 关闭（关闭后完全旁路）。
 *
 * <p><b>注册说明</b>：顺序取 {@link Ordered#HIGHEST_PRECEDENCE}，先于未声明顺序
 * （默认 {@code LOWEST_PRECEDENCE}）的 {@code Ddd4jWebFluxFilter} 执行；
 * 由 {@code ApiVersionWebFluxConfiguration} 注册为 WebFilter Bean（WebFlux 自动收集排序），
 * 未注册（或配置关闭不注册）时完全回退旧行为。
 *
 * @see ApiVersionResolver
 * @see io.ddd4j.web.webflux.ApiVersionWebFluxConfiguration
 */
public final class ApiVersionWebFilter implements WebFilter, Ordered {

    /** 解析出的 API 版本在 exchange 属性中的挂载键，供 {@code Ddd4jWebFluxFilter} 读取。 */
    public static final String API_VERSION_ATTRIBUTE = ApiVersionWebFilter.class.getName() + ".apiVersion";

    /** 版本解析器。 */
    private final ApiVersionResolver resolver;

    /** 是否启用；false 时完全旁路解析逻辑。 */
    private final boolean enabled;

    /**
     * 以启用状态创建过滤器。
     *
     * @param resolver 版本解析器（非 null）
     */
    public ApiVersionWebFilter(ApiVersionResolver resolver) {
        this(resolver, true);
    }

    /**
     * 创建过滤器。
     *
     * @param resolver 版本解析器（非 null）
     * @param enabled 是否启用；false 时直接放行且不挂载任何属性
     */
    public ApiVersionWebFilter(ApiVersionResolver resolver, boolean enabled) {
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
        this.enabled = enabled;
    }

    /**
     * 是否启用本过滤器的解析逻辑。
     *
     * @return 启用返回 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 过滤器顺序：先于上下文过滤器执行。
     *
     * @return {@link Ordered#HIGHEST_PRECEDENCE}
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * 解析 API 版本并挂载 exchange 属性。
     *
     * @param exchange 当前交换对象
     * @param chain 后续过滤链
     * @return 解析完成后继续放行的 {@code Mono}；严格模式非法值为 {@code Mono.error}
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }
        return Mono.defer(() -> {
            String headerVersion = exchange.getRequest().getHeaders().getFirst(XHeaders.X_APP_VERSION);
            String path = exchange.getRequest().getPath().value();
            ApiVersionResult result = resolver.resolve(headerVersion, path);
            exchange.getAttributes().put(API_VERSION_ATTRIBUTE, result.version());
            return chain.filter(exchange);
        });
    }
}
