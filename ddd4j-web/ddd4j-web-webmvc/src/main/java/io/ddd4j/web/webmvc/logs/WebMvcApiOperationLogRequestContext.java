package io.ddd4j.web.webmvc.logs;

import io.ddd4j.core.constant.XHeaders;
import io.ddd4j.data.logs.ApiOperationLogRequestContext;
import io.ddd4j.data.logs.aspect.ApiOperationLogAspect;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.webmvc.util.WebUtils;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Objects;

/** 兼容仅绑定 RequestContextHolder 的旧调用方，HTTP 采集由 WebMVC 接入层承担。 */
public final class WebMvcApiOperationLogRequestContext implements ApiOperationLogRequestContext {
    /** {@inheritDoc} */
    @Override
    public String requestId() {
        HttpServletRequest request = WebUtils.getHttpServletRequest();
        if (Objects.isNull(request)) {
            return ApiOperationLogRequestContext.super.requestId();
        }
        String parameter = request.getParameter(ApiOperationLogAspect.REQUEST_ID_KEY);
        return StrKit.hasText(parameter) ? parameter : request.getHeader(XHeaders.X_REQUEST_ID);
    }

    /** {@inheritDoc} */
    @Override
    public String requestUri() {
        HttpServletRequest request = WebUtils.getHttpServletRequest();
        return Objects.nonNull(request) ? request.getRequestURI() : ApiOperationLogRequestContext.super.requestUri();
    }

    /** {@inheritDoc} */
    @Override
    public String clientIp() {
        HttpServletRequest request = WebUtils.getHttpServletRequest();
        return Objects.nonNull(request) ? WebUtils.getRemoteAddr(request) : ApiOperationLogRequestContext.super.clientIp();
    }

    /** {@inheritDoc} */
    @Override
    public boolean includeArgument(Object argument) {
        // 保留原日志条件：仅同时实现 ServletRequest 与 ServletResponse 的参数被排除。
        return !(argument instanceof ServletRequest && argument instanceof ServletResponse);
    }
}
