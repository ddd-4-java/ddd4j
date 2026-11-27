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

import io.ddd4j.annotation.Contract;

import java.lang.annotation.*;

/**
 * Api模块注解
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Contract
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
public @interface ApiModule {

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

}
