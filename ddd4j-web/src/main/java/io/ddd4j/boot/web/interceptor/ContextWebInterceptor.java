package io.ddd4j.boot.web.interceptor;

import io.ddd4j.boot.core.context.ThreadContext;
import io.ddd4j.boot.kit.cache.RedisKit;
import io.ddd4j.boot.web.core.SessionContext;
import io.ddd4j.boot.web.utils.RequestContext;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;

import static io.ddd4j.boot.core.contract.constant.ContextConstants.*;

/**
 * 上下文Web拦截器
 *
 * @func 获取参数请求头，设到线程上下文以便后续使用
 */
@Slf4j
public class ContextWebInterceptor extends BaseWebInterceptor {
    public static final String THIRD_SESSION_PREFIX = "app:3rd_session:";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        ThreadContext.set(REQUEST_PARAMS, RequestContext.getParams());
        String tenantId = (request.getHeader("tenant_id") != null && !request.getHeader("tenant_id").isEmpty()) ? request.getHeader("tenant_id") : (request.getHeader("tenant-id") != null && !request.getHeader("tenant-id").isEmpty()) ? request.getHeader("tenant-id") : request.getHeader("tenantId");
        ThreadContext.set(tenantId != null && !tenantId.isEmpty(), TENANT_ID, tenantId);
        ThreadContext.set(SYSTEM_ID, request.getHeader(SYSTEM_ID));
        ThreadContext.set(THIRD_SESSION, request.getHeader(THIRD_SESSION));
        ThreadContext.set(CLIENT_TYPE, request.getHeader(CLIENT_TYPE));
        ThreadContext.set(APP_ID, request.getHeader(APP_ID));
        ThreadContext.set(SHOP_ID, request.getHeader(SHOP_ID));
        ThreadContext.set(ROLE, request.getIntHeader(ROLE));
        ThreadContext.set(AUTHORIZATION, request.getHeader(AUTHORIZATION));
        String acceptLanguage = request.getHeader("Accept-Language");
        // 解析第一个语言选项（如 "en-US,en;q=0.9" -> "en-US"）
        if (acceptLanguage != null && !acceptLanguage.isEmpty()) {
            ThreadContext.set(LOCALE, Locale.forLanguageTag(acceptLanguage.split(",")[0].replace('-', '_')));
        }
        if (request.getHeader(THIRD_SESSION) != null && !request.getHeader(THIRD_SESSION).isEmpty()) {
            try {
                //获取缓存中的ThirdSession
                SessionContext sessionContext = RedisKit.get(THIRD_SESSION_PREFIX + request.getHeader(THIRD_SESSION), SessionContext.class);
                if (sessionContext != null) {
                    ThreadContext.set(SESSION, sessionContext);
                    ThreadContext.set(USER_ID, sessionContext.getUserId());
                    log.info("获取SessionContext成功，userId={}", sessionContext.getUserId());
                } else {
                    log.warn("获取SessionContext失败，ThirdSession={}", request.getHeader(THIRD_SESSION));
                }
            } catch (Exception e) {
                log.warn("获取SessionContext失败", e);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        ThreadContext.clear();
    }

    @Override
    public int getOrder() {
        return -400;
    }
}