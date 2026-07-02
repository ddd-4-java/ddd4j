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

@Aspect
@Component
@Slf4j
/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class ApiOperationLogAspect {

    public static final String REQUEST_ID_KEY = "requestId";
    @Autowired
    private Snowflake snowflake;
    @Autowired
    private ApiOperationLogProvider logProvider;

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
