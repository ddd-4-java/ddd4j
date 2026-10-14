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
package io.ddd4j.web.validation.constraints;

import io.ddd4j.web.validation.constraintvalidators.NumberValueValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 数字取值校验注解：字符串形式的数字（可带负号）必须匹配指定正则。
 *
 * @since 2.0.x
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Constraint(validatedBy = {NumberValueValidator.class})
public @interface NumberValue {

    /**
     * 数字匹配正则，默认允许整数与负号。
     *
     * @return 正则表达式
     */
    String regex() default "^[0-9\\-]+$";

    /**
     * 校验失败时的提示消息。
     *
     * @return 提示消息
     */
    String message();

    /**
     * 分组校验约束集。
     *
     * @return 分组类型数组
     */
    Class<?>[] groups() default {};

    /**
     * 负载元数据约束集。
     *
     * @return 负载类型数组
     */
    Class<? extends Payload>[] payload() default {};
}
