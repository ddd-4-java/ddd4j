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

import io.ddd4j.web.core.error.WebStatusException;

import java.util.Objects;

/**
 * API 版本分发结果：携带请求版本、命中的可用版本、处理器句柄与是否回落等信息。
 *
 * <p>结果同时携带 HTTP 语义状态码：{@link #STATUS_OK} 分发成功（含优雅回落）、
 * {@link #STATUS_NOT_FOUND} 主版本无注册、{@link #STATUS_REJECTED} 严格模式拒绝。
 *
 * @param <T> 处理器类型
 * @see ApiVersionDispatchPolicy
 */
public final class ApiVersionDispatchResult<T> {

    /** 分发成功状态码（200），含优雅回落到最低可用版本的情形。 */
    public static final int STATUS_OK = 200;

    /** 请求主版本无任何注册处理器时的状态码（404 语义）。 */
    public static final int STATUS_NOT_FOUND = 404;

    /** 严格模式下请求版本低于最低可用版本时的状态码（400 语义）。 */
    public static final int STATUS_REJECTED = 400;

    /** 请求版本，恒非 null。 */
    private final ApiVersion requestedVersion;

    /** 命中的可用版本，未命中时为 null。 */
    private final ApiVersion matchedVersion;

    /** 命中的处理器句柄，未命中时为 null。 */
    private final T handler;

    /** 是否发生了回落（请求低于最低可用版本时优雅回落到最低版本）。 */
    private final boolean degraded;

    /** 是否带升级提示（回落时为 true）。 */
    private final boolean upgradeHint;

    /** HTTP 语义状态码。 */
    private final int status;

    /** 结果说明，用于日志与错误响应，可为 null。 */
    private final String message;

    /**
     * 创建分发结果（由 {@link ApiVersionDispatchPolicy} 调用）。
     *
     * @param requestedVersion 请求版本（非 null）
     * @param matchedVersion 命中的可用版本，可为 null
     * @param handler 命中的处理器句柄，可为 null
     * @param degraded 是否回落
     * @param upgradeHint 是否带升级提示
     * @param status HTTP 语义状态码
     * @param message 结果说明，可为 null
     */
    ApiVersionDispatchResult(ApiVersion requestedVersion, ApiVersion matchedVersion, T handler,
                             boolean degraded, boolean upgradeHint, int status, String message) {
        this.requestedVersion = Objects.requireNonNull(requestedVersion, "requestedVersion must not be null");
        this.matchedVersion = matchedVersion;
        this.handler = handler;
        this.degraded = degraded;
        this.upgradeHint = upgradeHint;
        this.status = status;
        this.message = message;
    }

    /**
     * 构造「主版本命中、未回落」的成功结果。
     *
     * @param <T> 处理器类型
     * @param requestedVersion 请求版本
     * @param matchedVersion 命中版本
     * @param handler 处理器句柄
     * @return 分发成功结果（200）
     */
    static <T> ApiVersionDispatchResult<T> matched(ApiVersion requestedVersion, ApiVersion matchedVersion, T handler) {
        return new ApiVersionDispatchResult<T>(requestedVersion, matchedVersion, handler,
                false, false, STATUS_OK, null);
    }

    /**
     * 构造「请求低于最低可用版本、优雅回落」的成功结果（200，带升级提示）。
     *
     * @param <T> 处理器类型
     * @param requestedVersion 请求版本
     * @param lowestVersion 最低可用版本
     * @param handler 最低版本对应的处理器句柄
     * @return 回落结果
     */
    static <T> ApiVersionDispatchResult<T> degraded(ApiVersion requestedVersion, ApiVersion lowestVersion, T handler) {
        return new ApiVersionDispatchResult<T>(requestedVersion, lowestVersion, handler,
                true, true, STATUS_OK,
                "requested version " + requestedVersion + " is below lowest available version "
                        + lowestVersion + ", gracefully downgraded");
    }

    /**
     * 构造「主版本无注册处理器」的 404 结果。
     *
     * @param <T> 处理器类型
     * @param requestedVersion 请求版本
     * @return 404 结果
     */
    static <T> ApiVersionDispatchResult<T> notFound(ApiVersion requestedVersion) {
        return new ApiVersionDispatchResult<T>(requestedVersion, null, null,
                false, false, STATUS_NOT_FOUND,
                "no registered handler for major version " + requestedVersion.major());
    }

    /**
     * 构造「严格模式下请求低于最低可用版本」的拒绝结果（400）。
     *
     * @param <T> 处理器类型
     * @param requestedVersion 请求版本
     * @param lowestVersion 最低可用版本
     * @return 400 拒绝结果
     */
    static <T> ApiVersionDispatchResult<T> rejected(ApiVersion requestedVersion, ApiVersion lowestVersion) {
        return new ApiVersionDispatchResult<T>(requestedVersion, null, null,
                false, false, STATUS_REJECTED,
                "requested version " + requestedVersion + " is below lowest available version " + lowestVersion);
    }

    /**
     * 请求版本。
     *
     * @return 请求版本值对象
     */
    public ApiVersion requestedVersion() {
        return requestedVersion;
    }

    /**
     * 命中的可用版本。
     *
     * @return 命中版本，未命中返回 null
     */
    public ApiVersion matchedVersion() {
        return matchedVersion;
    }

    /**
     * 命中的处理器句柄。
     *
     * @return 处理器句柄，未命中返回 null
     */
    public T handler() {
        return handler;
    }

    /**
     * 是否发生回落（请求低于最低可用版本被优雅降级）。
     *
     * @return 回落返回 {@code true}
     */
    public boolean degraded() {
        return degraded;
    }

    /**
     * 是否携带升级提示（与 {@link #degraded()} 同真）。
     *
     * @return 需要升级提示返回 {@code true}
     */
    public boolean upgradeHint() {
        return upgradeHint;
    }

    /**
     * HTTP 语义状态码：200/404/400。
     *
     * @return 状态码
     */
    public int status() {
        return status;
    }

    /**
     * 结果说明。
     *
     * @return 说明文本，可为 null
     */
    public String message() {
        return message;
    }

    /**
     * 是否命中了可用处理器。
     *
     * @return 命中返回 {@code true}
     */
    public boolean matched() {
        return Objects.nonNull(handler) && Objects.nonNull(matchedVersion);
    }

    /**
     * 取回命中的处理器，未命中时抛出携带结果状态码与说明的 {@link WebStatusException}，
     * 经既有异常翻译器即得 404/400 语义响应。
     *
     * @return 处理器句柄
     * @throws WebStatusException 未命中时抛出
     */
    public T requireMatch() {
        if (!matched()) {
            throw new WebStatusException(status, message);
        }
        return handler;
    }

    /**
     * 值相等判断：全字段参与比较。
     *
     * @param o 待比较对象
     * @return 全字段相等返回 {@code true}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApiVersionDispatchResult)) {
            return false;
        }
        ApiVersionDispatchResult<?> that = (ApiVersionDispatchResult<?>) o;
        return degraded == that.degraded
                && upgradeHint == that.upgradeHint
                && status == that.status
                && Objects.equals(requestedVersion, that.requestedVersion)
                && Objects.equals(matchedVersion, that.matchedVersion)
                && Objects.equals(handler, that.handler)
                && Objects.equals(message, that.message);
    }

    /**
     * 哈希码，与 {@link #equals(Object)} 保持一致。
     *
     * @return 哈希码
     */
    @Override
    public int hashCode() {
        return Objects.hash(requestedVersion, matchedVersion, handler, degraded, upgradeHint, status, message);
    }

    /**
     * 文本表示，便于日志输出与断言。
     *
     * @return 含全字段的描述字符串
     */
    @Override
    public String toString() {
        return "ApiVersionDispatchResult[requestedVersion=" + requestedVersion
                + ", matchedVersion=" + matchedVersion
                + ", handler=" + handler
                + ", degraded=" + degraded
                + ", upgradeHint=" + upgradeHint
                + ", status=" + status
                + ", message=" + message + "]";
    }
}
