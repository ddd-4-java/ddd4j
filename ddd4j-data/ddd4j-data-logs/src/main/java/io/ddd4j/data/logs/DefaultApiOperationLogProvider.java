package io.ddd4j.data.logs;

import io.ddd4j.core.constant.Constants;
import io.ddd4j.web.webmvc.util.WebUtils;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 默认 API 操作日志提供者实现
 * <p>通过 AOP 切面记录 API 调用日志，包括请求 URI、IP、参数、耗时等信息</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class DefaultApiOperationLogProvider implements ApiOperationLogProvider {

    @Override
    public void doBefore(JoinPoint joinPoint, Operation apiOperation) {

    }

    @Override
    public void afterReturing(JoinPoint joinPoint, Operation apiOperation, Object rt, com.google.common.base.Stopwatch stopWatch) {
        this.doApiOperationLog(joinPoint, apiOperation, rt, null, stopWatch);
    }

    @Override
    public void afterThrowing(JoinPoint joinPoint, Operation apiOperation, Throwable ex, com.google.common.base.Stopwatch stopWatch) {
        this.doApiOperationLog(joinPoint, apiOperation, null, ex, stopWatch);
    }

    /**
     * 执行操作日志记录
     *
     * @param joinPoint    连接点
     * @param apiOperation API 操作注解
     * @param rt           返回值
     * @param ex           异常信息
     * @param stopWatch    性能计时器
     */
    protected void doApiOperationLog(JoinPoint joinPoint, Operation apiOperation, Object rt, Throwable ex, com.google.common.base.Stopwatch stopWatch) {

        // 1、获取AOP信息
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;

        // 2、获取方法及参数信息
        Method method = methodSignature.getMethod();
        String methodName = methodSignature.getName();

        // 3、获取 Hidden 注解，如果获取到了，则不进行日志记录
        Hidden hidden = method.getAnnotation(Hidden.class);
        if (Objects.isNull(hidden)) {
            hidden = method.getDeclaringClass().getDeclaredAnnotation(Hidden.class);
        }

        // 4、判断是否需要记录日志
        boolean needLog = log.isInfoEnabled() && Objects.isNull(hidden);
        if (!needLog) {
            log.info(Constants.accessMarker, "Stopwatch: {}", stopWatch);
            return;
        }

        // 5、获取 Request对象，解析请求来源
        HttpServletRequest request = WebUtils.getHttpServletRequest();
        String uri = "";
        String ipAddress = "";
        if (Objects.nonNull(request)) {
            uri = request.getRequestURI();
            ipAddress = WebUtils.getRemoteAddr(request);
            log.info(Constants.accessMarker, " >> URI {} IP {} ", uri, ipAddress);
        }

        // 6、筛选出有意义的参数
        Object[] args = joinPoint.getArgs();
        List<Object> methodArgs = Objects.isNull(args) ? null : Stream.of(args)
                .filter(arg -> !(arg instanceof ServletRequest && arg instanceof ServletResponse))
                .collect(Collectors.toList());

        // 5、如果开启日志，则发送日志消息
        this.saveLog(joinPoint, method, apiOperation, rt, ex, stopWatch);

        if (Objects.isNull(ex)) {
            log.info(Constants.accessMarker, " >> invoke method {} with args {} Success! elapsed={}", methodName, methodArgs, stopWatch);
        } else {
            log.error(Constants.accessMarker, " >> invoke method {} with args {} error {} elapsed={}", methodName, methodArgs, ex.getMessage(), stopWatch);
        }

        log.info(Constants.accessMarker, "Stopwatch: {}", stopWatch);
    }

    /**
     * 保存日志（子类可重写此方法实现持久化）
     *
     * @param joinPoint    连接点
     * @param method       方法
     * @param apiOperation API 操作注解
     * @param rt           返回值
     * @param ex           异常信息
     * @param stopWatch    性能计时器
     */
    protected void saveLog(JoinPoint joinPoint, Method method, Operation apiOperation, Object rt, Throwable ex, com.google.common.base.Stopwatch stopWatch) {
        // do nothing
    }

}
