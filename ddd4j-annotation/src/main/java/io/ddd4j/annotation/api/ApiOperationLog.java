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
package io.ddd4j.annotation.api;

import io.ddd4j.annotation.BusinessType;

import java.lang.annotation.*;

/**
 * 操作日志注解
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@Inherited
public @interface ApiOperationLog {

    /**
     * \u64cd\u4f5c\u6a21\u5757
     *
     * @return \u64cd\u4f5c\u6a21\u5757
     */
    String module() default "";

    /**
     * \u4e1a\u52a1\u540d\u79f0
     *
     * @return \u4e1a\u52a1\u540d\u79f0
     */
    String business() default "";

    /**
     * \u64cd\u4f5c\u7c7b\u578b
     *
     * @return \u64cd\u4f5c\u7c7b\u578b
     */
    BusinessType opt();

    /**
     * \u662f\u5426\u9a6c\u4e0a\u5904\u7406
     *
     * @return \u662f\u5426\u7acb\u5373\u5904\u7406
     */
    boolean immediate() default false;

}
