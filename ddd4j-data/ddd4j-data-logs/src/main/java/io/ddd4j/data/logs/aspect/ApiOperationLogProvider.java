package io.ddd4j.data.logs.aspect;

import io.swagger.v3.oas.annotations.Operation;
import org.aspectj.lang.JoinPoint;
import org.springframework.util.StopWatch;

public interface ApiOperationLogProvider {


    default void doBefore(JoinPoint joinPoint, Operation apiOperation) {

    }

    default void afterReturing(JoinPoint joinPoint, Operation apiOperation, Object rt, StopWatch stopWatch) {

    }

    default Object wrapThrowing(JoinPoint joinPoint, Operation apiOperation, Throwable ex, StopWatch stopWatch) throws Throwable {
        throw ex;
    }

    default void afterThrowing(JoinPoint joinPoint, Operation apiOperation, Throwable ex, StopWatch stopWatch) {

    }

}
