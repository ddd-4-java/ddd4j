package io.ddd4j.boot.core.utils;

import io.ddd4j.boot.core.context.ThreadContext;
import io.ddd4j.boot.core.contract.constant.ContextConstants;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * 国际化工具类
 */
@Slf4j(topic = "### BASE-CORE : I18nKit ###")
@UtilityClass
public class I18nKit {
    // 国际化文件前缀
    private final String I18N_FILE_PREFIX = "i18n/messages";

    public String get(String source) {
        if (source == null || source.isEmpty()) return source;
        Locale locale = ThreadContext.get(ContextConstants.LOCALE);
        if (locale == null) {
//            log.debug("处理国际化失败，返回原内容");
            return source;
        }
        try {
            ResourceBundle resourceBundle = ResourceBundle.getBundle(I18N_FILE_PREFIX, locale);
            if (resourceBundle != null && resourceBundle.containsKey(source)) {
                return resourceBundle.getString(source);
            }
        } catch (Exception e) {
            log.warn("处理国际化异常", e);
        }
        return source;
    }

    public String get(String source, Object... args) {
        return format(get(source), args);
    }

    public void setLocale(Locale locale) {
        ThreadContext.set(ContextConstants.LOCALE, locale);
    }

    private String format(String template, Object... args) {
        if (template == null || template.isEmpty() || args == null || args.length == 0) {
            return template;
        }
        String placeHolder = "{}";
        final int strPatternLength = template.length();
        final int placeHolderLength = placeHolder.length();

        // 初始化定义好的长度以获得更好的性能
        final StringBuilder sbuf = new StringBuilder(strPatternLength + 50);

        int handledPosition = 0;// 记录已经处理到的位置
        int delimIndex;// 占位符所在位置
        for (int argIndex = 0; argIndex < args.length; argIndex++) {
            delimIndex = template.indexOf(placeHolder, handledPosition);
            if (delimIndex == -1) {// 剩余部分无占位符
                if (handledPosition == 0) { // 不带占位符的模板直接返回
                    return template;
                }
                // 字符串模板剩余部分不再包含占位符，加入剩余部分后返回结果
                sbuf.append(template, handledPosition, strPatternLength);
                return sbuf.toString();
            }

            // 转义符
            if (delimIndex > 0 && template.charAt(delimIndex - 1) == '\\') {// 转义符
                if (delimIndex > 1 && template.charAt(delimIndex - 2) == '\\') {// 双转义符
                    // 转义符之前还有一个转义符，占位符依旧有效
                    sbuf.append(template, handledPosition, delimIndex - 1);
                    sbuf.append(utf8Str(args[argIndex]));
                    handledPosition = delimIndex + placeHolderLength;
                } else {
                    // 占位符被转义
                    argIndex--;
                    sbuf.append(template, handledPosition, delimIndex - 1);
                    sbuf.append(placeHolder.charAt(0));
                    handledPosition = delimIndex + 1;
                }
            } else {// 正常占位符
                sbuf.append(template, handledPosition, delimIndex);
                sbuf.append(utf8Str(args[argIndex]));
                handledPosition = delimIndex + placeHolderLength;
            }
        }
        // 加入最后一个占位符后所有的字符
        sbuf.append(template, handledPosition, strPatternLength);
        return sbuf.toString();
    }

    /**
     * 将对象转为字符串
     * <pre>
     * 	 1、Byte数组和ByteBuffer会被转换为对应字符串的数组
     * 	 2、对象数组会调用Arrays.toString方法
     * </pre>
     *
     * @param obj 对象
     * @return 字符串
     */
    private String utf8Str(Object obj) {
        if (null == obj) {
            return null;
        }

        if (obj instanceof String) {
            return (String) obj;
        } else if (obj instanceof byte[]) {
            return str((byte[]) obj, StandardCharsets.UTF_8);
        } else if (obj instanceof Byte[]) {
            return str((Byte[]) obj, StandardCharsets.UTF_8);
        } else if (obj instanceof ByteBuffer) {
            return str((ByteBuffer) obj, StandardCharsets.UTF_8);
        } else if (obj.getClass().isArray()) {
            return toString(obj);
        }

        return obj.toString();
    }

    /**
     * 解码字节码
     *
     * @param data    字符串
     * @param charset 字符集，如果此字段为空，则解码的结果取决于平台
     * @return 解码后的字符串
     */
    private String str(byte[] data, Charset charset) {
        if (data == null) {
            return null;
        }

        if (null == charset) {
            return new String(data);
        }
        return new String(data, charset);
    }

    /**
     * 将编码的byteBuffer数据转换为字符串
     *
     * @param data    数据
     * @param charset 字符集，如果为空使用当前系统字符集
     * @return 字符串
     */
    private String str(ByteBuffer data, Charset charset) {
        if (null == charset) {
            charset = Charset.defaultCharset();
        }
        return charset.decode(data).toString();
    }

    /**
     * 解码字节码
     *
     * @param data    字符串
     * @param charset 字符集，如果此字段为空，则解码的结果取决于平台
     * @return 解码后的字符串
     */
    private String str(Byte[] data, Charset charset) {
        if (data == null) {
            return null;
        }

        byte[] bytes = new byte[data.length];
        Byte dataByte;
        for (int i = 0; i < data.length; i++) {
            dataByte = data[i];
            bytes[i] = (null == dataByte) ? -1 : dataByte;
        }

        return str(bytes, charset);
    }

    /**
     * 数组或集合转String
     *
     * @param obj 集合或数组对象
     * @return 数组字符串，与集合转字符串格式相同
     */
    private String toString(Object obj) {
        if (null == obj) {
            return null;
        }

        if (obj instanceof long[]) {
            return Arrays.toString((long[]) obj);
        } else if (obj instanceof int[]) {
            return Arrays.toString((int[]) obj);
        } else if (obj instanceof short[]) {
            return Arrays.toString((short[]) obj);
        } else if (obj instanceof char[]) {
            return Arrays.toString((char[]) obj);
        } else if (obj instanceof byte[]) {
            return Arrays.toString((byte[]) obj);
        } else if (obj instanceof boolean[]) {
            return Arrays.toString((boolean[]) obj);
        } else if (obj instanceof float[]) {
            return Arrays.toString((float[]) obj);
        } else if (obj instanceof double[]) {
            return Arrays.toString((double[]) obj);
        } else if (obj.getClass().isArray()) {
            // 对象数组
            try {
                return Arrays.deepToString((Object[]) obj);
            } catch (Exception ignore) {
                //ignore
            }
        }
        return obj.toString();
    }
}