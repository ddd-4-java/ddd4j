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
package io.ddd4j.extension.qlexpress.exception;

/**
 * QLExpress 表达式执行异常。
 */
public class QLExpressExecutionException extends RuntimeException {

    /** \u6267\u884c\u5931\u8d25\u7684\u8868\u8fbe\u5f0f\u3002 */
    private final String expression;

    /**
     * \u521b\u5efa\u5305\u542b\u5931\u8d25\u8868\u8fbe\u5f0f\u7684\u6267\u884c\u5f02\u5e38\u3002
     * @param expression \u5931\u8d25\u8868\u8fbe\u5f0f
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public QLExpressExecutionException(String expression, Throwable cause) {
        super("QLExpress 表达式执行失败: " + expression, cause);
        this.expression = expression;
    }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getExpression() {
        return expression;
    }
}
