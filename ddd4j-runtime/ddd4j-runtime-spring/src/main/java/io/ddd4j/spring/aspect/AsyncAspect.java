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
 * @deprecated 自 2.0.x 起下移到 {@code ddd4j-boot-spring-aspect.AsyncAspect}
 */
@Deprecated
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