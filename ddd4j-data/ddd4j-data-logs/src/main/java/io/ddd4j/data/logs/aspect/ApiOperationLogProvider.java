package io.ddd4j.data.logs.aspect;

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
    default void afterReturing(JoinPoint joinPoint, Operation apiOperation, Object rt, LogStopWatch stopWatch) {

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
    default Object wrapThrowing(JoinPoint joinPoint, Operation apiOperation, Throwable ex, LogStopWatch stopWatch) throws Throwable {
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
    default void afterThrowing(JoinPoint joinPoint, Operation apiOperation, Throwable ex, LogStopWatch stopWatch) {

    }

}
