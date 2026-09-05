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

package io.ddd4j.extension.qlexpress.model;

/**
 * 不抛出异常的表达式执行结果。
 *
 * @param success      是否成功
 * @param value        表达式原始结果值
 * @param errorCode    异常类型
 * @param errorMessage 异常消息
 * @param elapsedNanos 执行耗时，单位纳秒
 * @param <T>           结果类型
 */
public final class QLExpressExecutionResult<T> {

    private final boolean success;
    private final T value;
    private final String errorCode;
    private final String errorMessage;
    private final long elapsedNanos;

    private QLExpressExecutionResult(boolean success, T value, String errorCode,
                                     String errorMessage, long elapsedNanos) {
        this.success = success;
        this.value = value;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.elapsedNanos = elapsedNanos;
    }

    public static <T> QLExpressExecutionResult<T> success(T value, long elapsedNanos) {
        return new QLExpressExecutionResult<T>(true, value, null, null, elapsedNanos);
    }

    public static <T> QLExpressExecutionResult<T> failure(String errorCode, String errorMessage,
                                                          long elapsedNanos) {
        return new QLExpressExecutionResult<T>(false, null, errorCode, errorMessage, elapsedNanos);
    }

    public boolean success() { return success; }
    public T value() { return value; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public long elapsedNanos() { return elapsedNanos; }

    public boolean isSuccess() {
        return success;
    }

    public T getValue() {
        return value;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getElapsedNanos() {
        return elapsedNanos;
    }
}
