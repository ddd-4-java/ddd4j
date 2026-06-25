package io.ddd4j.data.logs.aspect;

import io.ddd4j.core.Constants;
import io.ddd4j.core.util.WebUtils;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StopWatch;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class DefaultApiOperationLogProvider implements ApiOperationLogProvider {

    @Override
    public void doBefore(JoinPoint joinPoint, Operation apiOperation) {

    }

    @Override
    public void afterReturing(JoinPoint joinPoint, Operation apiOperation, Object rt, StopWatch stopWatch) {
        this.doApiOperationLog(joinPoint, apiOperation, rt, null, stopWatch);
    }

    @Override
    public void afterThrowing(JoinPoint joinPoint, Operation apiOperation, Throwable ex, StopWatch stopWatch) {
        this.doApiOperationLog(joinPoint, apiOperation, null, ex, stopWatch);
    }

    protected void doApiOperationLog(JoinPoint joinPoint, Operation apiOperation, Object rt, Throwable ex, StopWatch stopWatch) {

        // 1、获取AOP信息
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;

        // 2、获取方法及参数信息
        Method method = methodSignature.getMethod();
        String methodName = methodSignature.getName();

        // 3、获取 Hidden 注解，如果获取到了，则不进行日志记录
        Hidden hidden = AnnotationUtils.findAnnotation(method, Hidden.class);
        if (Objects.isNull(hidden)) {
            hidden = AnnotationUtils.findAnnotation(method.getDeclaringClass(), Hidden.class);
        }

        // 4、判断是否需要记录日志
        boolean needLog = log.isInfoEnabled() && Objects.isNull(hidden);
        if (!needLog) {
            log.info(Constants.accessMarker, stopWatch.prettyPrint());
            return;
        }

        // 5、获取 Request对象，解析请求来源
        HttpServletRequest request = WebUtils.getHttpServletRequest();
        String uri = "";
        String ipAddress = "";
        if (Objects.nonNull(request)) {
            uri = request.getRequestURI();
            ipAddress = WebUtils.getRemoteAddr(request);
            log.info(Constants.accessMarker, "Request ID {} >> URI {} IP {} ", stopWatch.getId(), uri, ipAddress);
        }

        // 6、筛选出有意义的参数
        Object[] args = joinPoint.getArgs();
        List<Object> methodArgs = Objects.isNull(args) ? null : Stream.of(args)
                .filter(arg -> !(arg instanceof ServletRequest && arg instanceof ServletResponse))
                .collect(Collectors.toList());

        // 5、如果开启日志，则发送日志消息
        this.saveLog(joinPoint, method, apiOperation, rt, ex, stopWatch);

        if (Objects.isNull(ex)) {
            log.info(Constants.accessMarker, "Request ID {} >> invoke method {} with args {} Success!", stopWatch.getId(), methodName, methodArgs);
        } else {
            log.error(Constants.accessMarker, "Request ID {} >> invoke method {} with args {} error {} ", stopWatch.getId(), methodName, methodArgs, ex.getMessage());
        }

        log.info(Constants.accessMarker, stopWatch.prettyPrint());
    }

    protected void saveLog(JoinPoint joinPoint, Method method, Operation apiOperation, Object rt, Throwable ex, StopWatch stopWatch) {
        // do nothing
    }

}
