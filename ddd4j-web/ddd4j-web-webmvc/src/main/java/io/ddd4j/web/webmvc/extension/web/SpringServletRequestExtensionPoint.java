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

    String wrap(HttpServletRequest request, Map<String, Object> realParams);

    <T> RequestEntity<T> wrap(RequestEntity<T> requestEntity);

}
