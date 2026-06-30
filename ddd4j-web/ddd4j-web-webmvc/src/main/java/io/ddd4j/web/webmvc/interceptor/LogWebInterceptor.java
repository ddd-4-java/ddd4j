package io.ddd4j.web.webmvc.interceptor;

import io.ddd4j.web.config.BaseWebProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 日志Web拦截器
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @func 打印请求路径、请求方法
 */
@Slf4j
public class LogWebInterceptor extends BaseWebInterceptor {
    final ThreadLocal<LocalDateTime> beginTime = new ThreadLocal<>();
    @Autowired
    BaseWebProperties baseWebProperties;

    @Override
    public String[] pathPatterns() {
        return baseWebProperties.getLog().getIncludes().split(",");
    }

    @Override
    public String[] excludePathPatterns() {
        return baseWebProperties.getLog().getExcludes().split(",");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (handler.getClass() == ResourceHttpRequestHandler.class) {
            return Boolean.TRUE;
        }
        HandlerMethod method = (HandlerMethod) handler;
        String className = method.getBeanType().getSimpleName();
        String methodName = method.getMethod().getName();
        log.info("==> Request {} {} -> {}.{}()", request.getMethod(), request.getRequestURI(), className, methodName);
        beginTime.set(LocalDateTime.now());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        try {
            log.info("<== Response in {} ms {} {}",
                    Duration.between(beginTime.get(), LocalDateTime.now()).toMillis(),
                    request.getMethod(),
                    request.getRequestURI());
        } catch (Exception ignored) {
        } finally {
            beginTime.remove();
        }

    }

    @Override
    public int getOrder() {
        return -600;
    }
}
