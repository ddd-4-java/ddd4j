package io.ddd4j.boot.web.auth.interceptor;

import io.ddd4j.boot.core.context.SpringContext;
import io.ddd4j.boot.core.context.ThreadContext;
import io.ddd4j.boot.core.contract.exception.ServiceException;
import io.ddd4j.boot.core.utils.BizAssert;
import io.ddd4j.boot.web.auth.annotation.BaseAuth;
import io.ddd4j.boot.web.auth.annotation.Inside;
import io.ddd4j.boot.web.config.BaseWebProperties;
import io.ddd4j.boot.web.interceptor.BaseWebInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

import static io.ddd4j.boot.core.contract.constant.ContextConstants.AUTHORIZATION;
import static io.ddd4j.boot.core.contract.enums.ResultCode.FORBIDDEN;

@Slf4j(topic = "### BASE-WEB : BaseAuthInterceptor ###")
public class BaseAuthWebInterceptor extends BaseWebInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (handler.getClass() == ResourceHttpRequestHandler.class) return Boolean.TRUE;
        Method method = ((HandlerMethod) handler).getMethod();
        //判断访问的方法是否添加@Inside注解
        Inside insideMethod = ((HandlerMethod) handler).getMethodAnnotation(Inside.class);
        if (insideMethod != null) {
            return Boolean.TRUE;
        }
        BaseAuth baseAuth = method.getDeclaringClass().getDeclaredAnnotation(BaseAuth.class);
        if (baseAuth != null) {
            String bearerToken = ThreadContext.get(AUTHORIZATION);
            if (bearerToken == null || bearerToken.isEmpty()) {
                throw new ServiceException("bearerToken不能为空");
            }
            BaseWebProperties baseWebProperties = SpringContext.getBean(BaseWebProperties.class);
            if (!baseWebProperties.getAuth().getBearerTokens().contains(bearerToken.replaceFirst("Bearer ", ""))) {
                log.error("bearerTokens: {}", baseWebProperties.getAuth().getBearerTokens());
                throw new ServiceException(FORBIDDEN);
            }
        }
        return Boolean.TRUE;
    }

    @Override
    public int getOrder() {
        return -199;
    }
}