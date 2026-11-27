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
 * 表达式语法校验结果。
 */
public final class QLExpressValidationResult {

    /** \u8868\u8fbe\u5f0f\u662f\u5426\u901a\u8fc7\u8bed\u6cd5\u9a8c\u8bc1\u3002 */
    private final boolean valid;

    /** \u9a8c\u8bc1\u5931\u8d25\u65f6\u8fd4\u56de\u7ed9\u8c03\u7528\u65b9\u7684\u9519\u8bef\u8bf4\u660e\u3002 */
    private final String message;

    /**
     * 创建语法校验结果。
     *
     * @param valid   是否有效
     * @param message 校验消息
     */
    @JsonCreator()
    public QLExpressValidationResult(@JsonProperty("valid") boolean valid, @JsonProperty("message") String message) {
        this.valid = valid;
        this.message = message;
    }

    /**
     * 创建语法校验成功结果。
     *
     * @return 成功结果
     */
    public static QLExpressValidationResult success() {
        return new QLExpressValidationResult(true, "表达式语法正确");
    }

    /**
     * 创建语法校验失败结果。
     *
     * @param message 错误消息
     * @return 失败结果
     */
    public static QLExpressValidationResult invalid(String message) {
        return new QLExpressValidationResult(false, message);
    }

    /**
     * 返回校验状态，兼容 bean 调用方。
     *
     * @return 有效时为 true
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * 返回校验消息。
     *
     * @return 校验消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 读取校验状态。
     *
     * @return 有效时为 true
     */
    @JsonProperty("valid")
    public boolean valid() {
        return valid;
    }

    /**
     * 读取校验消息。
     *
     * @return 校验消息
     */
    @JsonProperty("message")
    public String message() {
        return message;
    }

    /**
     * 根据<code>message</code>、<code>valid</code>比较对象的值是否相等。
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
        QLExpressValidationResult other = (QLExpressValidationResult) obj;
        return this.valid == other.valid && Objects.equals(this.message, other.message);
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Boolean.hashCode(valid);
        result = 31 * result + Objects.hashCode(message);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>valid</code>、<code>message</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "QLExpressValidationResult[valid=" + valid + ", message=" + message + "]";
    }
}
