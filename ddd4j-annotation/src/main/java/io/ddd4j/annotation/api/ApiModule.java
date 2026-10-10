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
 */
@Contract
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
public @interface ApiModule {

    /**
     * 操作模块
     *
     * @return 归属的操作模块名称，默认空串
     */
    String module() default "";

    /**
     * 业务名称
     *
     * @return 归属的业务名称，默认空串
     */
    String business() default "";

}
