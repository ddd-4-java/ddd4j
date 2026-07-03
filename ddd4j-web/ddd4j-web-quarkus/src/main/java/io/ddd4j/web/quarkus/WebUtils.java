package io.ddd4j.web.quarkus;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import io.ddd4j.kit.lang.StrKit;
import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quarkus Web 层通用工具：从 Vert.x 请求解析语言/租户/用户，Excel 流式导出响应构建。
 * <p>
 * 对标 ddd4j-web 的 {@code WebUtils}（Spring 拦截器方案），Quarkus 轨道采用 Vert.x HttpServerRequest 方案。
 * </p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class WebUtils {

    private static final DateTimeFormatter RPT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final Map<String, String> LANG_MAP = new ConcurrentHashMap<>();

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
     * 将数据列表导出为 Excel 流式下载响应（Content-Disposition 附件、国际化表头）。
     *
     * @param lang      语言，用于表头国际化
     * @param sheetName 表名称文案 key（经 i18n）
     * @param dataList  行数据
     * @param head      Excel 表头/行类型
     * @return HTTP 响应，body 为 xlsx 流
     */
    public static <T> Response excel(String lang, String sheetName, List<T> dataList, Class<T> head) {
        StreamingOutput stream = outputStream -> {
            EasyExcel.write(outputStream, head)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet(i18n(lang, sheetName))
                    .doWrite(dataList);
        };
        String fileName = LocalDateTime.now().format(RPT_FORMAT) + ".xlsx";
        return Response.ok(stream)
                .header("Access-Control-Expose-Headers", "Content-Disposition")
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.ms-excel;charset=utf-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .build();
    }

    private static Locale resolveLocale(String lang) {
        if (StrKit.isBlank(lang)) {
            return Locale.getDefault();
        }
        return Locale.forLanguageTag(lang.replace('_', '-'));
    }
}
