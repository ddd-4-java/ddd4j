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
    /**
     * 返回当前请求的关联标识。
     *
     * <p>此实现调用 <code>WebUtils.getHttpServletRequest()</code>。</p>
     *
     * @return 根据执行路径返回<code>ApiOperationLogRequestContext.super.requestId()</code>、<code>StrKit.hasText(parameter) ? parameter : request.getHeader(XHeaders.X_REQUEST_ID)</code>
     */
    @Override
    public String requestId() {
        HttpServletRequest request = WebUtils.getHttpServletRequest();
        if (Objects.isNull(request)) {
            return ApiOperationLogRequestContext.super.requestId();
        }
        String parameter = request.getParameter(ApiOperationLogAspect.REQUEST_ID_KEY);
        return StrKit.hasText(parameter) ? parameter : request.getHeader(XHeaders.X_REQUEST_ID);
    }

    /**
     * 返回当前请求访问的 URI。
     *
     * <p>此实现调用 <code>WebUtils.getHttpServletRequest()</code>。</p>
     *
     * @return 实际返回<code>Objects.nonNull(request) ? request.getRequestURI() : ApiOperationLogRequestContext.super.requestUri()</code>
     */
    @Override
    public String requestUri() {
        HttpServletRequest request = WebUtils.getHttpServletRequest();
        return Objects.nonNull(request) ? request.getRequestURI() : ApiOperationLogRequestContext.super.requestUri();
    }

    /**
     * 返回当前请求解析出的客户端地址。
     *
     * <p>此实现调用 <code>WebUtils.getHttpServletRequest()</code>。</p>
     *
     * @return 当前客户端 IP；没有请求上下文时返回 null
     */
    @Override
    public String clientIp() {
        HttpServletRequest request = WebUtils.getHttpServletRequest();
        return Objects.nonNull(request) ? WebUtils.getRemoteAddr(request) : ApiOperationLogRequestContext.super.clientIp();
    }

    /**
     * 判断指定参数是否允许记录到接口操作日志。
     *
     * @param argument 连接点参数
     * @return 实际返回<code>!(argument instanceof ServletRequest &amp;&amp; argument instanceof ServletResponse)</code>，作为本次判断结果
     */
    @Override
    public boolean includeArgument(Object argument) {
        // 保留原日志条件：仅同时实现 ServletRequest 与 ServletResponse 的参数被排除。
        return !(argument instanceof ServletRequest && argument instanceof ServletResponse);
    }
}
