package io.ddd4j.extension.pf4j.point.web;

import jakarta.servlet.http.HttpServletResponse;
import org.pf4j.ExtensionPoint;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface ServletResponseExtensionPoint extends ExtensionPoint {

    String dewrap(HttpServletResponse response, Map<String, Object> realParams);

    <T> ResponseEntity<T> dewrap(ResponseEntity<T> responseEntity);

}
