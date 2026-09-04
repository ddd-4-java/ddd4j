package io.ddd4j.data.logs.aspect;

import cn.hutool.core.lang.Snowflake;
import com.google.common.base.Stopwatch;
import io.ddd4j.core.constant.XHeaders;
import io.ddd4j.data.logs.ApiOperationLogProvider;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.webmvc.util.WebUtils;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;

import java.util.Objects;

/**
 * API 操作日志切面
 * <p>拦截带有 {@link Operation} 注解的方法，自动记录请求日志、性能统计和异常信息</p>
 *
 * <p>纯 AspectJ 实现，不含 Spring 注解。Bean 装配由上层框架（如 ddd4j-boot-data-logs）通过
 * {@code @Bean} 构造方法注入完成。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Aspect
@Slf4j
public class ApiOperationLogAspect {

    /**
     * 请求 ID 参数名
     */
    public static final String REQUEST_ID_KEY = "requestId";
    /**
     * 雪花算法 ID 生成器
     */
    private final Snowflake snowflake;
    /**
     * 操作日志提供者
     */
    private final ApiOperationLogProvider logProvider;

    /**
     * 构造方法装配。
     *
     * @param snowflake   雪花算法 ID 生成器
     * @param logProvider 操作日志提供者
     */
    public ApiOperationLogAspect(Snowflake snowflake, ApiOperationLogProvider logProvider) {
        this.snowflake = snowflake;
        this.logProvider = logProvider;
    }

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

        // 1、创建并启动 Stopwatch
        String requestId = this.getRequestId();
        String taskName = Objects.nonNull(apiOperation.summary()) ? apiOperation.summary() : apiOperation.description();
        Stopwatch stopWatch = Stopwatch.createStarted();

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
            if (StrKit.hasText(parameterRequestId)) {
                requestId = parameterRequestId;
            } else {
                String headerRequestId = request.getHeader(XHeaders.X_REQUEST_ID);
                if (StrKit.hasText(headerRequestId)) {
                    requestId = headerRequestId;
                }
            }
        }
        if (!StrKit.hasText(requestId)) {
            requestId = snowflake.nextIdStr();
        }
        return requestId;
    }

}
