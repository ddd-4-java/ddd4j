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

import java.util.Objects;

/**
 * 表达式语法校验结果。
 */
public final class QLExpressValidationResult {

    private final boolean valid;
    private final String message;

/**
 * 表达式语法校验结果。
 */

    public QLExpressValidationResult(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    public static QLExpressValidationResult success() {
        return new QLExpressValidationResult(true, "表达式语法正确");
    }

    public static QLExpressValidationResult invalid(String message) {
        return new QLExpressValidationResult(false, message);
    }

    public boolean valid() { return valid; }
    public String message() { return message; }

    public boolean isValid() {
        return valid;
    }

    public String getMessage() {
        return message;
    }

    /** 按校验状态与消息比较结果，匹配 record 的值语义。 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QLExpressValidationResult)) {
            return false;
        }
        QLExpressValidationResult that = (QLExpressValidationResult) o;
        return valid == that.valid && Objects.equals(message, that.message);
    }

    /** 返回与 record 组件顺序一致的哈希。 */
    @Override
    public int hashCode() {
        return 31 * Boolean.hashCode(valid) + Objects.hashCode(message);
    }

    /** 返回跨版本一致的校验结果文本。 */
    @Override
    public String toString() {
        return "QLExpressValidationResult[valid=" + valid + ", message=" + message + ']';
    }
}
