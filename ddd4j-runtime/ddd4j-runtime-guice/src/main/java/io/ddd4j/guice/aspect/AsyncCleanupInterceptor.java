/*
 * Copyright 2017-2026 the original author hiwepy.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.guice.aspect;

import io.ddd4j.core.context.ThreadContext;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * 异步方法 ThreadContext 清理拦截器（等价于 Spring 的 AsyncAspect）。
 * <p>
 * 在异步方法执行后清理 ThreadLocal（ThreadContext.clear()），
 * 防止线程池复用时 ThreadLocal 泄漏。
 * <p>
 * 使用方式：
 * <pre>{@code
 * // 在 Guice Module 中配置
 * bindInterceptor(Matchers.any(), Matchers.annotatedWith(Async.class),
 *     new AsyncCleanupInterceptor());
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class AsyncCleanupInterceptor implements MethodInterceptor {

    /**
     * \u6267\u884c\u5f02\u6b65\u88ab\u62e6\u622a\u65b9\u6cd5\uff0c\u5e76\u6839\u636e\u7ed3\u679c\u5b89\u6392\u7ebf\u7a0b\u4e0a\u4e0b\u6587\u6e05\u7406\u3002
     * @param invocation \u5f85\u6267\u884c\u7684\u62e6\u622a\u65b9\u6cd5\u8c03\u7528
     * @return \u5f02\u6b65\u8c03\u7528\u6216\u76ee\u6807\u65b9\u6cd5\u8fd4\u56de\u7684\u7ed3\u679c
     * @throws Throwable \u76ee\u6807\u65b9\u6cd5\u6267\u884c\u6216\u5176\u5f02\u6b65\u5305\u88c5\u5931\u8d25
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        try {
            return invocation.proceed();
        } finally {
            try {
                ThreadContext.clear();
                if (log.isTraceEnabled()) {
                    log.trace("ThreadContext cleared after async method: {}.{}",
                            invocation.getMethod().getDeclaringClass().getSimpleName(),
                            invocation.getMethod().getName());
                }
            } catch (Exception e) {
                log.warn("Failed to clear ThreadContext after async method", e);
            }
        }
    }
}
