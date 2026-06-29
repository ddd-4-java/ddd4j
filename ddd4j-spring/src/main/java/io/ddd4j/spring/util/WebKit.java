package io.ddd4j.spring.util;

import cn.hutool.json.JSONUtil;
import io.ddd4j.core.XHeaders;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.biz.utils.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.PrintWriter;


@Slf4j
public class WebKit extends org.springframework.biz.utils.WebUtils {

    public static String getHeader(String name) {
        HttpServletRequest request = getHttpServletRequest();
        Assert.notNull(request, "request from RequestContextHolder is null");
        return request.getHeader(name);
    }

    /**
     * 读取cookie
     *
     * @param name cookie name
     * @return cookie value
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     */
    public static String getCookieVal(String name) {
        HttpServletRequest request = getHttpServletRequest();
        Assert.notNull(request, "request from RequestContextHolder is null");
        return getCookieVal(request, name);
    }

    /**
     * 读取cookie
     *
     * @param request HttpServletRequest
     * @param name    cookie name
     * @return cookie value
     */
    public static String getCookieVal(HttpServletRequest request, String name) {
        Cookie cookie = getCookie(request, name);
        return cookie != null ? cookie.getValue() : null;
    }

    /**
     * 清除 某个指定的cookie
     *
     * @param response HttpServletResponse
     * @param key      cookie key
     */
    public static void removeCookie(HttpServletResponse response, String key) {
        setCookie(response, key, null, 0);
    }

    /**
     * 设置cookie
     *
     * @param response        HttpServletResponse
     * @param name            cookie name
     * @param value           cookie value
     * @param maxAgeInSeconds maxage
     */
    public static void setCookie(HttpServletResponse response, String name, String value, int maxAgeInSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeInSeconds);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }

    /**
     * 返回json
     *
     * @param response HttpServletResponse
     * @param result   结果对象
     */
    public static void renderJson(HttpServletResponse response, Object result) {
        renderJson(response, result, MediaType.APPLICATION_JSON_VALUE);
    }

    /**
     * 返回json
     *
     * @param response    HttpServletResponse
     * @param result      结果对象
     * @param contentType contentType
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

    public static String getDeviceId(HttpServletRequest request) {
        // 1、判断是否 Apple 设备
        String deviceId = request.getHeader(XHeaders.X_DEVICE_IDFA);
        if (!StringUtils.hasText(deviceId)) {
            deviceId = request.getHeader(XHeaders.X_DEVICE_OAID);
        }
        if (!StringUtils.hasText(deviceId)) {
            deviceId = request.getHeader(XHeaders.X_DEVICE_OPENUDID);
        }
        // 2、判断是否 Android 设备
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

    public static HttpServletRequest getHttpServletRequest() {
        try {
            RequestAttributes requestAttributes = getRequestAttributesSafely();
            if (requestAttributes != null) {
                return ((ServletRequestAttributes) requestAttributes).getRequest();
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }

    public static RequestAttributes getRequestAttributesSafely() {
        RequestAttributes requestAttributes = null;
        try {
            requestAttributes = RequestContextHolder.currentRequestAttributes();
        } catch (IllegalStateException e) {

        }
        return requestAttributes;
    }

}
