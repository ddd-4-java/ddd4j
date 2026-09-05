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
package io.ddd4j.spring.aspect;

import io.ddd4j.core.context.ThreadContext;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

/**
 * 异步方法 ThreadLocal 清理切面（Spring AOP）。
 *
 * <p><b>迁移说明</b>：自 2.0.x 起，本类将从 {@code ddd4j-runtime-spring} 下移到
 * {@code ddd4j-boot-spring-aspect}（Spring Boot starter）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Aspect
public class AsyncAspect {

    // 定义一个切入点，匹配所有标记了 @Async 的方法
    @Pointcut("@annotation(org.springframework.scheduling.annotation.Async)")
    public void asyncMethod() {
    }

    // 在方法执行后清理 ThreadLocal 数据，避免线程变量污染
    @After("asyncMethod()")
    public void afterAsyncMethod() {
        ThreadContext.clear();
    }
}