package io.ddd4j.data.logs.aspect;

import cn.hutool.core.lang.Snowflake;
import io.ddd4j.core.constant.XHeaders;
import io.ddd4j.spring.util.WebUtils;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * API 操作日志切面
 * <p>拦截带有 {@link Operation} 注解的方法，自动记录请求日志、性能统计和异常信息</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Aspect
@Component
@Slf4j
public class ApiOperationLogAspect {

    /** 请求 ID 参数名 */
    public static final String REQUEST_ID_KEY = "requestId";
    /** 雪花算法 ID 生成器 */
    @Autowired
    private Snowflake snowflake;
    /** 操作日志提供者 */
    @Autowired
    private ApiOperationLogProvider logProvider;

    /**
     * 环绕通知：拦截带有 @Operation 注解的方法
     *
     * @param pjd          连接点
     * @param apiOperation API 操作注解
     * @return 方法执行结果
     * @throws Throwable 执行异常
     */
    @Around("@annotation(io.swagger.v3.oas.annotations.Operation) && @annotation(apiOperation)")
    public Object aroundMethod(ProceedingJoinPoint pjd, Operation apiOperation) throws Throwable {

        // 1、创建并启动 StopWatch
        String requestId = this.getRequestId();
        StopWatch stopWatch = new StopWatch(requestId);
        stopWatch.start(Objects.nonNull(apiOperation.summary()) ? apiOperation.summary() : apiOperation.description());

        try {

            // 2、开启日志记录
            logProvider.doBefore(pjd, apiOperation);

            // 3、执行代理方法
            Object result = null;
            try {
                result = pjd.proceed();
                return result;
            } finally {
                if (stopWatch.isRunning()) {
                    stopWatch.stop();
                }
                // 4、记录访问日志
                logProvider.afterReturing(pjd, apiOperation, result, stopWatch);
            }
        } catch (Throwable ex) {
            log.debug("Method invoke error !", ex);
            try {
                if (stopWatch.isRunning()) {
                    stopWatch.stop();
                }
                return logProvider.wrapThrowing(pjd, apiOperation, ex, stopWatch);
            } finally {
                // 5、记录异常日志
                logProvider.afterThrowing(pjd, apiOperation, ex, stopWatch);
            }
        } finally {
            MDC.clear();
        }
    }

    /**
     * 获取请求 ID（优先从参数或请求头获取，否则由雪花算法生成）
     *
     * @return 请求 ID
     */
    public String getRequestId() {
        HttpServletRequest request = WebUtils.getHttpServletRequest();
        String requestId = null;
        if (Objects.nonNull(request)) {
            String parameterRequestId = request.getParameter(REQUEST_ID_KEY);
            if (StringUtils.hasText(parameterRequestId)) {
                requestId = parameterRequestId;
            } else {
                String headerRequestId = request.getHeader(XHeaders.X_REQUEST_ID);
                if (StringUtils.hasText(headerRequestId)) {
                    requestId = headerRequestId;
                }
            }
        }
        if (!StringUtils.hasText(requestId)) {
            requestId = snowflake.nextIdStr();
        }
        return requestId;
    }

}
