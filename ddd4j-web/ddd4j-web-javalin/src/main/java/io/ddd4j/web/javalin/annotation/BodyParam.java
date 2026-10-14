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
package io.ddd4j.web.javalin.annotation;
import java.lang.annotation.Target;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Retention;
import java.lang.annotation.Inherited;
import java.lang.annotation.ElementType;
import java.lang.annotation.Documented;

import java.lang.annotation.*;

/**
 * 方法参数注解：将请求体（Body）绑定到控制器方法参数。
 *
 * <p>配合参数名解析，将原始请求体或反序列化结果注入标注了本注解的参数。
 *
 * @since 2.0.x
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface BodyParam {

    /**
     * 参数名（为空时使用方法参数名）。
     *
     * @return 参数名
     */
    String value() default "";

    /**
     * 是否为必填参数。
     *
     * @return {@code true} 表示缺少请求体时抛出异常
     */
    boolean required() default true;
}
