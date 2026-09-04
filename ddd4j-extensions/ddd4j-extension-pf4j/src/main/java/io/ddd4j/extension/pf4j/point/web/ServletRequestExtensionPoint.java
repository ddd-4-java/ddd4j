package io.ddd4j.extension.pf4j.point.web;

import javax.servlet.http.HttpServletRequest;
import org.pf4j.ExtensionPoint;
import org.springframework.http.RequestEntity;

import java.util.Map;

public interface ServletRequestExtensionPoint extends ExtensionPoint {

    String wrap(HttpServletRequest request, Map<String, Object> realParams);

    <T> RequestEntity<T> wrap(RequestEntity<T> requestEntity);

}
