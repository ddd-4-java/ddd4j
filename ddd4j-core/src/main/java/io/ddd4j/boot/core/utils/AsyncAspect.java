package io.ddd4j.boot.core.utils;

import io.ddd4j.boot.core.context.ThreadContext;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

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