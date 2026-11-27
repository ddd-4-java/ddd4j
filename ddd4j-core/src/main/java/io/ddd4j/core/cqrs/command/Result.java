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
package io.ddd4j.core.cqrs.command;

import lombok.Getter;

import java.util.Optional;

/**
 * CQRS 命令执行结果。
 * <p>
 * 与 HTTP 响应 {@code io.ddd4j.core.api.R} 区分：
 * <ul>
 *   <li>{@link Result}：命令执行结果（领域层）</li>
 *   <li>{@code R}：HTTP 响应包装（接口层）</li>
 * </ul>
 *
 * @param <T> 业务数据类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@Getter
public final class Result<T> {

    /**
     * 成功状态码。
     */
    public static final int SUCCESS = 0;

    /**
     * 失败状态码。
     */
    public static final int FAILURE = 1;

    /** \u547d\u4ee4\u7ed3\u679c\u7684\u6570\u503c\u72b6\u6001\u7801\u3002 */
    private final int code;
    /** \u547d\u4ee4\u6267\u884c\u7684\u8bf4\u660e\u6216\u5931\u8d25\u539f\u56e0\u3002 */
    private final String message;
    /** \u547d\u4ee4\u6267\u884c\u8fd4\u56de\u7684\u4e1a\u52a1\u6570\u636e\u3002 */
    private final T data;

    /**
     * \u521b\u5efa\u5305\u542b\u4e1a\u52a1\u72b6\u6001\u3001\u8bf4\u660e\u548c\u53ef\u9009\u6570\u636e\u7684\u547d\u4ee4\u7ed3\u679c\u3002
     * @param code \u4e1a\u52a1\u72b6\u6001\u7801
     * @param message \u6267\u884c\u7ed3\u679c\u8bf4\u660e
     * @param data \u4e1a\u52a1\u8fd4\u56de\u6570\u636e
     */
    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * \u6210\u529f\u7ed3\u679c\uff08\u65e0\u6570\u636e\uff09\u3002
     *
     * @return \u4e0d\u5e26\u4e1a\u52a1\u6570\u636e\u7684\u6210\u529f\u7ed3\u679c
     */
    public static Result<Void> ok() {
        return new Result<>(SUCCESS, "ok", null);
    }

    /**
     * \u6210\u529f\u7ed3\u679c\uff08\u5e26\u6570\u636e\uff09\u3002
     *
     * @param <T> \u4e1a\u52a1\u6570\u636e\u7c7b\u578b
     * @param data \u4e1a\u52a1\u6570\u636e
     * @return \u643a\u5e26\u6570\u636e\u7684\u6210\u529f\u7ed3\u679c
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS, "ok", data);
    }

    /**
     * \u5931\u8d25\u7ed3\u679c\u3002
     *
     * @param <T> \u4e1a\u52a1\u6570\u636e\u7c7b\u578b
     * @param message \u5931\u8d25\u6d88\u606f
     * @return \u4e0d\u5e26\u4e1a\u52a1\u6570\u636e\u7684\u5931\u8d25\u7ed3\u679c
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(FAILURE, message, null);
    }

    /**
     * \u5931\u8d25\u7ed3\u679c\uff08\u5e26\u9519\u8bef\u7801\uff09\u3002
     *
     * @param <T> \u4e1a\u52a1\u6570\u636e\u7c7b\u578b
     * @param code \u9519\u8bef\u7801
     * @param message \u5931\u8d25\u6d88\u606f
     * @return \u5e26\u6307\u5b9a\u9519\u8bef\u7801\u7684\u5931\u8d25\u7ed3\u679c
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 判断布尔状态。
     * @return 判断结果。
     */
    public boolean isSuccess() {
        return code == SUCCESS;
    }

    /**
     * \u4ee5 Optional \u8fd4\u56de\u4e1a\u52a1\u6570\u636e\u3002
     * @return \u4e1a\u52a1\u6570\u636e\u7684 Optional
     */
    public Optional<T> data() {
        return Optional.ofNullable(data);
    }
}
