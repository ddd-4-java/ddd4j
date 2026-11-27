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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * 不抛出异常的表达式执行结果。
 *
 * @param <T> 结果类型
 */

public final class QLExpressExecutionResult<T> {

    /**
     * 表达式是否正常完成。
     */
    private final boolean success;

    /**
     * 表达式成功执行后返回的值。
     */
    private final T value;

    /**
     * 稳定标识执行失败类别的错误码。
     */
    private final String errorCode;

    /**
     * 表达式执行失败时返回的诊断说明。
     */
    private final String errorMessage;

    /**
     * 单次执行从开始到结束的耗时，单位为纳秒。
     */
    private final long elapsedNanos;

    /**
     * @param success      是否成功
     * @param value        表达式原始结果值
     * @param errorCode    异常类型
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

    /**
     * 创建成功执行结果。
     *
     * @param <T>          结果类型
     * @param value        表达式结果
     * @param elapsedNanos 执行耗时（纳秒）
     * @return 成功结果
     */
    public static <T> QLExpressExecutionResult<T> success(T value, long elapsedNanos) {
        return new QLExpressExecutionResult<>(true, value, null, null, elapsedNanos);
    }

    /**
     * 创建失败执行结果。
     *
     * @param <T>          结果类型
     * @param errorCode    错误码
     * @param errorMessage 错误消息
     * @param elapsedNanos 执行耗时（纳秒）
     * @return 失败结果
     */
    public static <T> QLExpressExecutionResult<T> failure(String errorCode, String errorMessage, long elapsedNanos) {
        return new QLExpressExecutionResult<>(false, null, errorCode, errorMessage, elapsedNanos);
    }

    /**
     * 获取执行耗时。
     *
     * @return 执行耗时（纳秒）
     */
    public long getElapsedNanos() {
        return elapsedNanos;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 获取错误消息。
     *
     * @return 错误消息
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 获取表达式结果。
     *
     * @return 表达式结果
     */
    public T getValue() {
        return value;
    }

    /**
     * 判断执行是否成功。
     *
     * @return 成功时为 true
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 读取成功标记。
     *
     * @return 成功时为 true
     */
    @JsonProperty("success")
    public boolean success() {
        return success;
    }

    /**
     * 读取表达式结果。
     *
     * @return 表达式结果
     */
    @JsonProperty("value")
    public T value() {
        return value;
    }

    /**
     * 读取错误码。
     *
     * @return 错误码
     */
    @JsonProperty("errorCode")
    public String errorCode() {
        return errorCode;
    }

    /**
     * 读取错误消息。
     *
     * @return 错误消息
     */
    @JsonProperty("errorMessage")
    public String errorMessage() {
        return errorMessage;
    }

    /**
     * 读取执行耗时。
     *
     * @return 执行耗时（纳秒）
     */
    @JsonProperty("elapsedNanos")
    public long elapsedNanos() {
        return elapsedNanos;
    }

    /**
     * 根据<code>value</code>、<code>errorCode</code>、<code>errorMessage</code>、<code>success</code>、<code>elapsedNanos</code>比较对象的值是否相等。
     *
     * <p>只比较运行时类型相同的对象。</p>
     *
     * @param obj 用来比较或转换的输入对象
     * @return 对象满足此实现的相等条件时为真
     */
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

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
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

    /**
     * 生成用于调试和日志的对象描述，包含<code>success</code>、<code>value</code>、<code>errorCode</code>、<code>errorMessage</code>、<code>elapsedNanos</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "QLExpressExecutionResult[success=" + success + ", value=" + value + ", errorCode=" + errorCode + ", errorMessage=" + errorMessage + ", elapsedNanos=" + elapsedNanos + "]";
    }
}
