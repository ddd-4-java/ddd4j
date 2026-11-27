package io.ddd4j.web.webmvc.extension.web;

import jakarta.servlet.http.HttpServletRequest;
import org.pf4j.ExtensionPoint;
import org.springframework.http.RequestEntity;

import java.util.Map;

/**
 * Spring HTTP Servlet 请求扩展点。
 *
 * <p>从 io.github.hiwepy:pf4j-extension 迁入至 ddd4j-web-webmvc 模块，
 * 因为它强依赖 Spring {@link RequestEntity}，属于 Spring Web 适配层职责。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface SpringServletRequestExtensionPoint extends ExtensionPoint {

    /**
     * \u5305\u88c5\u5916\u90e8\u8bf7\u6c42\u3002
     * @param request HTTP \u8bf7\u6c42
     * @param realParams \u539f\u59cb\u8bf7\u6c42\u53c2\u6570
     * @return \u5305\u88c5\u540e\u7684\u8bf7\u6c42
     */
    String wrap(HttpServletRequest request, Map<String, Object> realParams);

    /**
     * \u5305\u88c5\u5916\u90e8\u8bf7\u6c42\u3002
     * @param requestEntity \u8bf7\u6c42\u5b9e\u4f53
     * @param <T> \u8bf7\u6c42\u4f53\u7c7b\u578b
     * @return \u5305\u88c5\u540e\u7684\u8bf7\u6c42
     */
    <T> RequestEntity<T> wrap(RequestEntity<T> requestEntity);

}
