package io.ddd4j.web.webmvc.util;

import cn.hutool.json.JSONUtil;
import io.ddd4j.core.constant.XHeaders;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;

/**
 * Web 工具类（Spring WebMVC 扩展）。
 * <p>提供请求头获取、Cookie 操作、JSON 渲染和设备 ID 解析等实用方法。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class WebKit extends org.springframework.extension.utils.WebUtils {

    /**
     * 根据名称获取请求头值。
     *
     * @param name 请求头名称
     * @return 请求头值
     */
    public static String getHeader(String name) {
        HttpServletRequest request = getHttpServletRequest();
        Assert.notNull(request, "request from RequestContextHolder is null");
        return request.getHeader(name);
    }

    /**
     * 读取 cookie
     *
     * @param name cookie name
     * @return cookie value
     */
    public static String getCookieVal(String name) {
        HttpServletRequest request = getHttpServletRequest();
        Assert.notNull(request, "request from RequestContextHolder is null");
        return getCookieVal(request, name);
    }

    /**
     * 读取 cookie
     *
     * @param request HttpServletRequest
     * @param name    cookie name
     * @return cookie value
     */
    public static String getCookieVal(HttpServletRequest request, String name) {
        Cookie cookie = getCookie(request, name);
        return Objects.nonNull(cookie) ? cookie.getValue() : null;
    }

    /**
     * 清除某个指定的 cookie。
     *
     * @param response HttpServletResponse
     * @param key      cookie key
     */
    public static void removeCookie(HttpServletResponse response, String key) {
        setCookie(response, key, null, 0);
    }

    /**
     * 设置 cookie。
     *
     * @param response        HttpServletResponse
     * @param name            cookie name
     * @param value           cookie value
     * @param maxAgeInSeconds 最大存活时间（秒）
     */
    public static void setCookie(HttpServletResponse response, String name, String value, int maxAgeInSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeInSeconds);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }

    /**
     * 返回 JSON 格式响应。
     *
     * @param response HttpServletResponse
     * @param result   结果对象
     */
    public static void renderJson(HttpServletResponse response, Object result) {
        renderJson(response, result, MediaType.APPLICATION_JSON_VALUE);
    }

    /**
     * 返回 JSON 格式响应（指定 Content-Type）。
     *
     * @param response    HttpServletResponse
     * @param result      结果对象
     * @param contentType Content-Type
     */
    public static void renderJson(HttpServletResponse response, Object result, String contentType) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType(contentType);
        try (PrintWriter out = response.getWriter()) {
            out.append(JSONUtil.toJsonStr(result));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 从请求头中获取设备 ID。
     * <p>依次尝试 IDFA、OAID、OpenUDID、IMEI、AndroidID 等头信息。</p>
     *
     * @param request HttpServletRequest
     * @return 设备 ID
     */
    public static String getDeviceId(HttpServletRequest request) {
        String deviceId = request.getHeader(XHeaders.X_DEVICE_IDFA);
        if (!StringUtils.hasText(deviceId)) {
            deviceId = request.getHeader(XHeaders.X_DEVICE_OAID);
        }
        if (!StringUtils.hasText(deviceId)) {
            deviceId = request.getHeader(XHeaders.X_DEVICE_OPENUDID);
        }
        if (!StringUtils.hasText(deviceId)) {
            deviceId = request.getHeader(XHeaders.X_DEVICE_IMEI);
        }
        if (!StringUtils.hasText(deviceId)) {
            deviceId = request.getHeader(XHeaders.X_DEVICE_ANDROIDID);
        }
        if (!StringUtils.hasText(deviceId)) {
            deviceId = request.getHeader(XHeaders.X_DEVICE_OAID);
        }
        return deviceId;
    }

    /**
     * 获取当前 HttpServletRequest。
     *
     * @return HttpServletRequest，可能为 null
     */
    public static HttpServletRequest getHttpServletRequest() {
        try {
            RequestAttributes requestAttributes = getRequestAttributesSafely();
            if (Objects.nonNull(requestAttributes)) {
                return ((ServletRequestAttributes) requestAttributes).getRequest();
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }

    /**
     * 安全获取 RequestAttributes。
     *
     * @return RequestAttributes，可能为 null
     */
    public static RequestAttributes getRequestAttributesSafely() {
        RequestAttributes requestAttributes = null;
        try {
            requestAttributes = RequestContextHolder.currentRequestAttributes();
        } catch (IllegalStateException e) {
        }
        return requestAttributes;
    }

}
