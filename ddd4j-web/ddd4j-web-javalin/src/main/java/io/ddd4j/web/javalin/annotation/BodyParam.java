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
 * \u65b9\u6cd5\u53c2\u6570\u6ce8\u89e3\uff1a\u5c06\u8bf7\u6c42\u4f53\uff08Body\uff09\u7ed1\u5b9a\u5230\u63a7\u5236\u5668\u65b9\u6cd5\u53c2\u6570\u3002
 *
 *  <p>\u914d\u5408\u53c2\u6570\u540d\u89e3\u6790\uff0c\u5c06\u539f\u59cb\u8bf7\u6c42\u4f53\u6216\u53cd\u5e8f\u5217\u5316\u7ed3\u679c\u6ce8\u5165\u6807\u6ce8\u4e86\u672c\u6ce8\u89e3\u7684\u53c2\u6570\u3002
 * @since 2.0.x
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface BodyParam {
    /**
     * \u53c2\u6570\u540d\uff08\u4e3a\u7a7a\u65f6\u4f7f\u7528\u65b9\u6cd5\u53c2\u6570\u540d\uff09\u3002
     * @return \u53c2\u6570\u540d
     */
    String value() default "";

    /**
     * \u662f\u5426\u4e3a\u5fc5\u586b\u53c2\u6570\u3002
     * @return {@code true} \u8868\u793a\u7f3a\u5c11\u8bf7\u6c42\u4f53\u65f6\u629b\u51fa\u5f02\u5e38
     */
    boolean required() default true;
}
