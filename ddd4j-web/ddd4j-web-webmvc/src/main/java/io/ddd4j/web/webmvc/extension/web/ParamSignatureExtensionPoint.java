package io.ddd4j.web.webmvc.extension.web;

import jakarta.servlet.http.HttpServletRequest;
import org.pf4j.ExtensionPoint;
import org.pf4j.PluginRuntimeException;

import java.util.Map;

/**
 * 参数签名扩展点（Spring Web 适配）。
 *
 * <p>从 ddd4j-extension-pf4j 迁入至 ddd4j-web-webmvc 模块，
 * 因为它使用 Servlet API，属于 Web 适配层职责。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface ParamSignatureExtensionPoint extends ExtensionPoint {

    void sign(HttpServletRequest request, Map<String, Object> params) throws PluginRuntimeException;

    String getAppkey(String appid);

    String getAppSecret(String appid);

}
