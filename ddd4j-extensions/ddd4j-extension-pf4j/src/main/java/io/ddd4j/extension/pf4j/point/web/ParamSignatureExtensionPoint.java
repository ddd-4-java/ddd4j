package io.ddd4j.extension.pf4j.point.web;

import jakarta.servlet.http.HttpServletRequest;
import org.pf4j.ExtensionPoint;
import org.pf4j.PluginRuntimeException;

import java.util.Map;

/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface ParamSignatureExtensionPoint extends ExtensionPoint {

    void sign(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    String getAppkey(String appid);

    String getAppSecret(String appid);

}
