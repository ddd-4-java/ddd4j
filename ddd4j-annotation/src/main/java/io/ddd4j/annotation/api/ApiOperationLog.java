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
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@Inherited
public @interface ApiOperationLog {

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

    /**
     * 操作类型
     *
     * @return 操作类型枚举，必填
     */
    BusinessType opt();

    /**
     * 是否马上处理
     *
     * @return 是否立即记录操作日志，默认 false
     */
    boolean immediate() default false;

}
