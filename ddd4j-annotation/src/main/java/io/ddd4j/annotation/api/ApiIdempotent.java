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
 * API 幂等性注解。
 *
 * <p>用于标注 Controller 方法或类，启用接口幂等性保护。
 * 支持基于请求参数（ARGS）或 Token 的幂等控制策略，可配置过期时间、重试次数等参数。
 */
@Contract
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ApiIdempotent {

    /**
     * 幂等Key：默认为空；
     * type为Args时自动获取 @RequestMapping、@PostMapping、@GetMapping、@PutMapping、@DeleteMapping、@PatchMapping 的 value 值；
     * type为Token时该值用于告诉拦截器取值的参数名
     *
     * @return 幂等 Key 表达式；策略为 ARGS 时按接口映射取值，否则作为取值参数名
     */
    String value() default "";

    /**
     * 幂等方式
     *
     * @return 幂等性判定策略，默认基于请求参数
     */
    ApiIdempotentType type() default ApiIdempotentType.ARGS;

    /**
     * 是否启用 Spring Expression Language(SpEL) 表达式解析value值
     *
     * @return 是否启用 SpEL 解析，默认 false
     */
    boolean spel() default false;

    /**
     * 是否将参数作为幂等key的一部分
     *
     * @return 是否把请求参数纳入幂等 Key，默认 false
     */
    boolean withArgs() default false;

    /**
     * 幂等过期时间，默认 2000 毫秒，即：在此时间段内，对API进行幂等处理。
     *
     * @return 幂等保护的过期时间，单位毫秒，默认 2000
     */
    long expireMillis() default 2000;

    /**
     * 重试次数，默认0
     *
     * @return 幂等冲突时的重试次数，默认 0
     */
    int retryTimes() default 0;

    /**
     * 重试间隔时间，单位：ms，默认100
     *
     * @return 两次重试之间的间隔，单位毫秒，默认 100
     */
    long retryInterval() default 100;

    /**
     * 是否自动进行解锁操作，默认：false, 等待key过期
     *
     * @return 是否自动解锁，默认 false，即等待 Key 自然过期
     */
    boolean unlock() default false;

}
