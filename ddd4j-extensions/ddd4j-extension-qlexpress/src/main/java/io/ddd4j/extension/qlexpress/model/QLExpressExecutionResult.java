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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/**
 * 不抛出异常的表达式执行结果。
 *
 * @param <T> 结果类型
 */

public final class QLExpressExecutionResult<T> {

    private static final long serialVersionUID = 0L;

    private final boolean success;

    private final T value;

    private final String errorCode;

    private final String errorMessage;

    private final long elapsedNanos;

    public static <T> QLExpressExecutionResult<T> success(T value, long elapsedNanos) {
        return new QLExpressExecutionResult<>(true, value, null, null, elapsedNanos);
    }

    public static <T> QLExpressExecutionResult<T> failure(String errorCode, String errorMessage, long elapsedNanos) {
        return new QLExpressExecutionResult<>(false, null, errorCode, errorMessage, elapsedNanos);
    }

    public long getElapsedNanos() {
        return elapsedNanos;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public T getValue() {
        return value;
    }

    public boolean isSuccess() {
        return success;
    }

    /**
 * @param success 是否成功
 * @param value 表达式原始结果值
 * @param errorCode 异常类型
 * @param errorMessage 异常消息
 * @param elapsedNanos 执行耗时，单位纳秒
 */

    @JsonCreator()
    public QLExpressExecutionResult(@JsonProperty("success") boolean success, @JsonProperty("value") T value, @JsonProperty("errorCode") String errorCode, @JsonProperty("errorMessage") String errorMessage, @JsonProperty("elapsedNanos") long elapsedNanos) {
        this.success = success;
        this.value = value;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.elapsedNanos = elapsedNanos;
    }

    @JsonProperty("success")
    public boolean success() {
        return success;
    }

    @JsonProperty("value")
    public T value() {
        return value;
    }

    @JsonProperty("errorCode")
    public String errorCode() {
        return errorCode;
    }

    @JsonProperty("errorMessage")
    public String errorMessage() {
        return errorMessage;
    }

    @JsonProperty("elapsedNanos")
    public long elapsedNanos() {
        return elapsedNanos;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        QLExpressExecutionResult<?> other = (QLExpressExecutionResult<?>) obj;
        return this.success == other.success && Objects.equals(this.value, other.value) && Objects.equals(this.errorCode, other.errorCode) && Objects.equals(this.errorMessage, other.errorMessage) && this.elapsedNanos == other.elapsedNanos;
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Boolean.hashCode(success);
        result = 31 * result + Objects.hashCode(value);
        result = 31 * result + Objects.hashCode(errorCode);
        result = 31 * result + Objects.hashCode(errorMessage);
        result = 31 * result + Long.hashCode(elapsedNanos);
        return result;
    }

    @Override
    public String toString() {
        return "QLExpressExecutionResult[success=" + success + ", value=" + value + ", errorCode=" + errorCode + ", errorMessage=" + errorMessage + ", elapsedNanos=" + elapsedNanos + "]";
    }
}
