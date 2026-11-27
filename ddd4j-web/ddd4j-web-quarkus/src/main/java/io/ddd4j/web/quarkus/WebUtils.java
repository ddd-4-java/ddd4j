package io.ddd4j.web.quarkus;

import io.ddd4j.kit.lang.StrKit;
import io.vertx.core.http.HttpServerRequest;

import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quarkus Web 层通用工具：从 Vert.x 请求解析语言、租户和用户上下文。
 * <p>
 * 对标 ddd4j-web 的 {@code WebUtils}（Spring 拦截器方案），Quarkus 轨道采用 Vert.x HttpServerRequest 方案。
 * </p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class WebUtils {

    /** \u5c06\u5ba2\u6237\u7aef\u8bed\u8a00\u6807\u8bb0\u6620\u5c04\u6210 Locale \u7684\u8868\u3002 */
    private static final Map<String, String> LANG_MAP = new ConcurrentHashMap<>();

    /** \u52a0\u8f7d Web \u672c\u5730\u5316\u63d0\u793a\u6587\u672c\u65f6\u4f7f\u7528\u7684\u8d44\u6e90\u5305\u540d\u3002 */
    private static final String BUNDLE_BASENAME = "i18n/messages";

    static {
        LANG_MAP.put("en-us", "en");
        LANG_MAP.put("zh-cn", "zh");
        LANG_MAP.put("zh-hk", "zh-tw");
        LANG_MAP.put("english", "en");
        LANG_MAP.put("chinese", "zh");
        LANG_MAP.put("portuguese", "pt");
        LANG_MAP.put("vietnamese", "vi");
        LANG_MAP.put("spanish", "es");
        LANG_MAP.put("russian", "ru");
        LANG_MAP.put("japanese", "ja");
    }

    /**
     * 从 Accept-Language 解析语言；空或 {@code *} 时使用默认语言，并映射到系统支持的语言代码。
     *
     * @param request Vert.x 请求
     * @return 归一化后的语言字符串
     */
    public static String getLang(HttpServerRequest request) {
        String lan = request.getHeader("Accept-Language");
        if (StrKit.isEmpty(lan) || lan.startsWith("*")) {
            lan = Locale.getDefault().toLanguageTag();
        } else {
            if (lan.contains(",")) {
                lan = lan.substring(0, lan.indexOf(","));
            } else if (lan.contains(";")) {
                lan = lan.substring(0, lan.indexOf(";"));
            }
        }
        lan = lan.toLowerCase();
        return LANG_MAP.getOrDefault(lan, lan);
    }

    /**
     * 从请求头解析租户 ID（site、tenantId、tenant-id、tenant_id）。
     *
     * @param request Vert.x 请求
     * @return 租户 ID，可能为空
     */
    public static String getTenantId(HttpServerRequest request) {
        String site = request.getHeader("site");
        if (StrKit.isEmpty(site)) {
            site = request.getHeader("tenantId");
        }
        if (StrKit.isEmpty(site)) {
            site = request.getHeader("tenant-id");
        }
        if (StrKit.isEmpty(site)) {
            site = request.getHeader("tenant_id");
        }
        if (StrKit.isEmpty(site)) {
            site = request.getParam("tenant-id");
        }
        if (StrKit.isEmpty(site)) {
            site = request.getParam("site");
        }
        return site;
    }

    /**
     * 从请求头 {@code uid} 读取当前用户 ID。
     *
     * @param request Vert.x 请求
     * @return 用户 ID，可能为空
     */
    public static String getUid(HttpServerRequest request) {
        return request.getHeader("uid");
    }

    /**
     * 按语言做国际化翻译。
     *
     * @param lang       语言
     * @param key        i18n 键
     * @param parameters 占位参数
     * @return 翻译后字符串
     */
    public static String i18n(String lang, String key, Object... parameters) {
        if (StrKit.isBlank(key)) {
            return null;
        }
        Locale locale = resolveLocale(lang);
        String pattern = key;
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASENAME, locale);
            pattern = bundle.getString(key);
        } catch (MissingResourceException ignored) {
            // 找不到资源时返回原始 key
        }
        return Objects.isNull(parameters) || parameters.length == 0 ? pattern : MessageFormat.format(pattern, parameters);
    }

    /**
     * \u5c06\u8bed\u8a00\u4ee3\u7801\u5206\u9694\u7b26\u89c4\u8303\u5316\u540e\u8f6c\u6362\u4e3a Locale\uff0c\u7a7a\u767d\u503c\u56de\u9000\u5230\u9ed8\u8ba4\u533a\u57df\u3002
     * @param lang \u8c03\u7528\u65b9\u4f20\u5165\u7684\u8bed\u8a00\u4e0e\u5730\u533a\u6807\u8bb0
     * @return \u8bf7\u6c42\u5bf9\u5e94\u7684\u533a\u57df
     */
    private static Locale resolveLocale(String lang) {
        if (StrKit.isBlank(lang)) {
            return Locale.getDefault();
        }
        return Locale.forLanguageTag(lang.replace('_', '-'));
    }
}
