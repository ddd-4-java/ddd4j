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
package io.ddd4j.data.logs;

import com.google.common.base.Stopwatch;
import io.swagger.v3.oas.annotations.Operation;
import org.aspectj.lang.JoinPoint;

/**
 * API 操作日志提供者接口
 * <p>定义操作日志的记录生命周期方法，支持前置、返回、异常等阶段的日志处理</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface ApiOperationLogProvider {

    /**
     * 方法执行前调用
     *
     * @param joinPoint    连接点
     * @param apiOperation API 操作注解
     */
    default void doBefore(JoinPoint joinPoint, Operation apiOperation) {

    }

    /**
     * 方法正常返回后调用
     *
     * @param joinPoint    连接点
     * @param apiOperation API 操作注解
     * @param rt           返回值
     * @param stopWatch    性能计时器
     */
    default void afterReturing(JoinPoint joinPoint, Operation apiOperation, Object rt, Stopwatch stopWatch) {

    }

    /**
     * 方法抛出异常时包装处理
     *
     * @param joinPoint    连接点
     * @param apiOperation API 操作注解
     * @param ex           异常
     * @param stopWatch    性能计时器
     * @return 包装后的返回值
     * @throws Throwable 原始异常
     */
    default Object wrapThrowing(JoinPoint joinPoint, Operation apiOperation, Throwable ex, com.google.common.base.Stopwatch stopWatch) throws Throwable {
        throw ex;
    }

    /**
     * 方法抛异常后调用
     *
     * @param joinPoint    连接点
     * @param apiOperation API 操作注解
     * @param ex           异常
     * @param stopWatch    性能计时器
     */
    default void afterThrowing(JoinPoint joinPoint, Operation apiOperation, Throwable ex, com.google.common.base.Stopwatch stopWatch) {

    }

}
