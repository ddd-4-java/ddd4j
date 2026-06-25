package io.ddd4j.boot.cmpt.pf4j.point.web;

import jakarta.servlet.http.HttpServletRequest;
import org.pf4j.ExtensionPoint;
import org.springframework.http.RequestEntity;

import java.util.Map;

public interface ServletRequestExtensionPoint extends ExtensionPoint {

    String wrap(HttpServletRequest request, Map<String, Object> realParams);

    <T> RequestEntity<T> wrap(RequestEntity<T> requestEntity);

}
