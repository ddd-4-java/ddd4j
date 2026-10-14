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
package io.ddd4j.web.core.version;

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.core.context.WebRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * API 版本解析器：按冻结的固定优先级解析客户端请求的 API 版本。
 *
 * <p>优先级（冻结）：{@code X-App-Version} 请求头 &gt; URL 路径版本段（如 {@code /v2/orders} 中的 {@code v2}）
 * &gt; 配置默认版本。
 *
 * <p>双模式处理（{@link ApiVersionMode}）：
 * <ul>
 *   <li>{@link ApiVersionMode#FALLBACK}：非法版本值回退默认版本（记 debug 日志）；</li>
 *   <li>{@link ApiVersionMode#STRICT}：非法版本值抛出 {@link ApiVersionRejectedException}（4xx 拒绝），
 *       严格模式下非法请求头不再落到路径与默认版本。</li>
 * </ul>
 *
 * <p>头与路径版本不一致时以请求头为准，并记 debug 日志。纯 Java 实现，不依赖 Spring，
 * 运行时由 WebMVC 拦截器 / WebFlux 过滤器接线调用。
 *
 * @see ApiVersion
 * @see ApiVersionDispatchPolicy
 */
public final class ApiVersionResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ApiVersionResolver.class);

    /**
     * 路径版本段形态：完整的 v2、v2.1、v2.1.0 段（大小写不敏感，前后无多余内容）。
     */
    private static final Pattern PATH_VERSION_PATTERN =
            Pattern.compile("^v\\d+(?:\\.\\d+){0,2}$", Pattern.CASE_INSENSITIVE);

    /** 配置默认版本，头与路径双缺失时兜底。 */
    private final ApiVersion defaultVersion;

    /** 非法版本值的处理模式。 */
    private final ApiVersionMode mode;

    /**
     * 创建解析器。
     *
     * @param defaultVersion 配置默认版本（非 null）
     * @param mode 非法版本值处理模式（非 null）
     */
    public ApiVersionResolver(ApiVersion defaultVersion, ApiVersionMode mode) {
        this.defaultVersion = Objects.requireNonNull(defaultVersion, "defaultVersion must not be null");
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
    }

    /**
     * 以回退（FALLBACK）模式创建解析器。
     *
     * @param defaultVersion 配置默认版本（非 null）
     */
    public ApiVersionResolver(ApiVersion defaultVersion) {
        this(defaultVersion, ApiVersionMode.FALLBACK);
    }

    /**
     * 配置默认版本。
     *
     * @return 默认版本值对象
     */
    public ApiVersion defaultVersion() {
        return defaultVersion;
    }

    /**
     * 非法版本值的处理模式。
     *
     * @return 模式枚举
     */
    public ApiVersionMode mode() {
        return mode;
    }

    /**
     * 仅有请求上下文可用时的解析入口（对齐设计类图 {@code resolve(WebRequestContext)}）：
     * 上下文不携带自定义请求头，因此按「路径 &gt; 默认版本」解析。
     *
     * @param context 请求上下文（非 null）
     * @return 解析结果
     */
    public ApiVersionResult resolve(WebRequestContext context) {
        WebRequestContext requestContext = Objects.requireNonNull(context, "context must not be null");
        return resolve(null, requestContext.path());
    }

    /**
     * 按冻结优先级解析版本。
     *
     * @param headerVersion {@code X-App-Version} 请求头值，可为 null 或空白（表示未携带）
     * @param path 请求路径，可为 null 或空白（表示未知路径）
     * @return 解析结果，恒非 null
     * @throws ApiVersionRejectedException {@link ApiVersionMode#STRICT} 且请求头版本值非法时抛出
     */
    public ApiVersionResult resolve(String headerVersion, String path) {
        if (StrKit.isNotBlank(headerVersion)) {
            return resolveFromHeader(headerVersion, path);
        }
        String pathSegment = findPathVersionSegment(path);
        if (StrKit.isNotBlank(pathSegment)) {
            return new ApiVersionResult(ApiVersion.parse(pathSegment), ApiVersionSource.PATH, pathSegment);
        }
        return new ApiVersionResult(defaultVersion, ApiVersionSource.DEFAULT, defaultVersion.toString());
    }

    /**
     * 请求头存在时的解析：合法则以头为准（与路径不一致时记 debug 日志），
     * 非法则按模式回退默认版本或抛出拒绝异常。
     *
     * @param headerVersion 请求头原文（非空白）
     * @param path 请求路径，可为 null 或空白
     * @return 以请求头或默认版本构成的解析结果
     * @throws ApiVersionRejectedException 严格模式且请求头版本值非法时抛出
     */
    private ApiVersionResult resolveFromHeader(String headerVersion, String path) {
        String trimmed = headerVersion.trim();
        ApiVersion headerParsed;
        try {
            headerParsed = ApiVersion.parse(trimmed);
        } catch (IllegalArgumentException exception) {
            if (mode == ApiVersionMode.STRICT) {
                throw new ApiVersionRejectedException("illegal api version value: " + trimmed);
            }
            LOG.debug("illegal api version header falls back to default version: header={}, default={}",
                    trimmed, defaultVersion);
            return new ApiVersionResult(defaultVersion, ApiVersionSource.DEFAULT, defaultVersion.toString());
        }
        String pathSegment = findPathVersionSegment(path);
        if (StrKit.isNotBlank(pathSegment) && !ApiVersion.parse(pathSegment).equals(headerParsed)) {
            LOG.debug("api version header differs from path segment, header wins: header={}, path={}",
                    headerParsed, pathSegment);
        }
        return new ApiVersionResult(headerParsed, ApiVersionSource.HEADER, trimmed);
    }

    /**
     * 扫描路径中的首个版本段（形如 {@code v2}、{@code v2.1.0}）。
     *
     * @param path 请求路径，可为 null 或空白
     * @return 首个匹配的路径版本段原文，无匹配返回 null
     */
    private String findPathVersionSegment(String path) {
        if (StrKit.isBlank(path)) {
            return null;
        }
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (StrKit.isNotBlank(segment) && PATH_VERSION_PATTERN.matcher(segment).matches()) {
                return segment;
            }
        }
        return null;
    }
}
