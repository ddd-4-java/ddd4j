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

import io.ddd4j.web.validation.constraintvalidators.PhoneValueValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 电话号码校验注解：按语种规则校验电话号码格式。
 *
 * @since 2.0.x
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Constraint(validatedBy = {PhoneValueValidator.class})
public @interface PhoneNumber {

    /**
     * 语种/地区规则，默认 {@code CN}。
     *
     * @return 语种编码
     */
    String lang() default "CN";

    /**
     * 约束分组标识（保留属性）。
     *
     * @return 标识值
     */
    String value() default "";

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
