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

import java.lang.annotation.*;

/**
 * \u65b9\u6cd5\u53c2\u6570\u6ce8\u89e3\uff1a\u6309\u540d\u79f0\u7ed1\u5b9a\u8868\u5355\uff08Form\uff09\u53c2\u6570\u5230\u63a7\u5236\u5668\u65b9\u6cd5\u53c2\u6570\u3002
 * @since 2.0.x
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface FormParam {
    /**
     * \u8868\u5355\u53c2\u6570\u540d\u3002
     * @return \u8868\u5355\u53c2\u6570\u540d
     */
    String value();

    /**
     * \u53c2\u6570\u7f3a\u5931\u65f6\u4f7f\u7528\u7684\u7f3a\u7701\u503c\u3002
     * @return \u7f3a\u7701\u503c
     */
    String defaultValue() default "";
}
