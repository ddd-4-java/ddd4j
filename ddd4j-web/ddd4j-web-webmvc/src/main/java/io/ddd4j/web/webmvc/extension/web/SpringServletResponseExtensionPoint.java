package io.ddd4j.web.webmvc.extension.web;

import jakarta.servlet.http.HttpServletResponse;
import org.pf4j.ExtensionPoint;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Spring HTTP Servlet 响应扩展点。
 *
 * <p>从 ddd4j-extension-pf4j 迁入至 ddd4j-web-webmvc 模块，
 * 因为它强依赖 Spring {@link ResponseEntity}，属于 Spring Web 适配层职责。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface SpringServletResponseExtensionPoint extends ExtensionPoint {

    String dewrap(HttpServletResponse response, Map<String, Object> realParams);

    <T> ResponseEntity<T> dewrap(ResponseEntity<T> responseEntity);

}
