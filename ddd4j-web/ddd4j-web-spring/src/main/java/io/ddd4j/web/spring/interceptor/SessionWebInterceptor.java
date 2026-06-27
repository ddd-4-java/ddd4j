package io.ddd4j.web.spring.interceptor;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.contract.exception.ServiceException;
import io.ddd4j.kit.cache.RedisKit;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.auth.annotation.Inside;
import io.ddd4j.web.core.SessionContext;
import io.ddd4j.web.interceptor.BaseWebInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;

import static io.ddd4j.core.contract.constant.ContextConstants.*;
import static io.ddd4j.core.contract.enums.ResultCode.*;

/**
 * Session拦截器，拦截以/api、/client开头的接口
 */
@Slf4j
public class SessionWebInterceptor extends BaseWebInterceptor {
    public static final String THIRD_SESSION_PREFIX = "app:3rd_session:";
    private static final long TIME_OUT_SESSION = 24 * 5 * 3600;

    @Override
    public String[] pathPatterns() {
        return new String[]{"/api/**", "/client/**"};
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (handler.getClass() == ResourceHttpRequestHandler.class) return Boolean.TRUE;
        HandlerMethod method = (HandlerMethod) handler;
        //判断访问的Controller或方法是否添加@Inside注解
        Inside insideType = method.getMethod().getDeclaringClass().getDeclaredAnnotation(Inside.class);
        Inside insideMethod = method.getMethodAnnotation(Inside.class);
        if (insideType != null || insideMethod != null) {
            return Boolean.TRUE;
        }
        //小程序端或带有appId的微信H5的所有接口需要登录才能访问，校验thirdSession
        String thirdSession = ThreadContext.get(THIRD_SESSION);
        if (StrKit.isBlank(thirdSession)) {
            throw new ServiceException(NO_SESSION);
        }
        // 校验session
        SessionContext sessionContext = ThreadContext.get(SESSION);
        //判断session是否过期，或session是否属于当前tenantId、appId
        if (sessionContext == null || !Objects.equals(sessionContext.getTenantId(), ThreadContext.get(TENANT_ID)) || ((sessionContext.getAppId() != null && !sessionContext.getAppId().isEmpty()) && !sessionContext.getAppId().equals(ThreadContext.get(APP_ID)))) {
            throw new ServiceException(TIMEOUT);
        }
        if (!Objects.equals(sessionContext.getTenantId(), ThreadContext.get(TENANT_ID)) || ((sessionContext.getAppId() != null && !sessionContext.getAppId().isEmpty()) && !sessionContext.getAppId().equals(ThreadContext.get(APP_ID)))) {
            throw new ServiceException(TIMEOUT);
        }
        if (StrKit.isNotBlank(sessionContext.getAppId()) && !Objects.equals(sessionContext.getAppId(), ThreadContext.get(APP_ID))) {
            throw new ServiceException(TIMEOUT);
        }
        // 必须登录才能访问
        if (StrKit.isBlank(sessionContext.getUserId())) {
            throw new ServiceException(LOGIN_FIRST);
        }
        // Session续期
        if (ThreadContext.contains(SESSION)) {
            RedisKit.expire(THIRD_SESSION_PREFIX + ThreadContext.get(THIRD_SESSION), TIME_OUT_SESSION);
        }
        ThreadContext.remove(THIRD_SESSION);
        return Boolean.TRUE;
    }

    @Override
    public int getOrder() {
        return -200;
    }
}