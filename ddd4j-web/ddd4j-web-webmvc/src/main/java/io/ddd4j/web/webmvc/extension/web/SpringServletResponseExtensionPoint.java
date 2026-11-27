package io.ddd4j.web.webmvc.extension.web;

import jakarta.servlet.http.HttpServletResponse;
import org.pf4j.ExtensionPoint;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Spring HTTP Servlet 响应扩展点。
 *
 * <p>从 io.github.hiwepy:pf4j-extension 迁入至 ddd4j-web-webmvc 模块，
 * 因为它强依赖 Spring {@link ResponseEntity}，属于 Spring Web 适配层职责。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface SpringServletResponseExtensionPoint extends ExtensionPoint {

    /**
     * \u89e3\u5305\u5916\u90e8\u54cd\u5e94\u3002
     * @param response HTTP \u54cd\u5e94
     * @param realParams \u539f\u59cb\u8bf7\u6c42\u53c2\u6570
     * @return \u89e3\u5305\u540e\u7684\u54cd\u5e94
     */
    String dewrap(HttpServletResponse response, Map<String, Object> realParams);

    /**
     * \u89e3\u5305\u5916\u90e8\u54cd\u5e94\u3002
     * @param responseEntity \u54cd\u5e94\u5b9e\u4f53
     * @param <T> \u54cd\u5e94\u4f53\u7c7b\u578b
     * @return \u89e3\u5305\u540e\u7684\u54cd\u5e94
     */
    <T> ResponseEntity<T> dewrap(ResponseEntity<T> responseEntity);

}
