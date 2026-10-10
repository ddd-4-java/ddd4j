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

import io.ddd4j.web.validation.constraintvalidators.StringDateValueValidator;
import javax.validation.Constraint;
import javax.validation.Payload;

import java.lang.annotation.*;

/**
 * 日期字符串校验注解：字符串日期必须匹配指定的日期模式。
 *
 * @since 2.0.x
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Constraint(validatedBy = {StringDateValueValidator.class})
public @interface StringDateValue {

    /**
     * 日期模式，默认 {@code yyyy-MM-dd}。
     *
     * @return 日期格式
     */
    String pattern() default "yyyy-MM-dd";

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
