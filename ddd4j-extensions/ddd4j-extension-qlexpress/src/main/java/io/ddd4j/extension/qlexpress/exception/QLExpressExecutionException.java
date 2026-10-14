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

    /** 执行失败的表达式。 */
    private final String expression;

    /**
     * 创建包含失败表达式的执行异常。
     *
     * @param expression 失败表达式
     * @param cause 原始异常
     */
    public QLExpressExecutionException(String expression, Throwable cause) {
        super("QLExpress 表达式执行失败: " + expression, cause);
        this.expression = expression;
    }

    /**
     * 获取失败表达式。
     *
     * @return 失败表达式
     */
    public String getExpression() {
        return expression;
    }
}
