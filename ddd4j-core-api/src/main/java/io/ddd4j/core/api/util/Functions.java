package io.ddd4j.core.api.util;

import java.util.Objects;
import java.util.function.Function;

/**
 * 通用函数式接口集合
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
public final class Functions {

    private Functions() {
    }

    /**
     * Object -> String 转换函数
     */
    public static final Function<Object, String> TO_STRING = obj -> Objects.isNull(obj) ? null : String.valueOf(obj);

    /**
     * Object -> Integer 转换函数
     */
    public static final Function<Object, Integer> TO_INTEGER = obj -> {
        if (Objects.isNull(obj)) {
            return null;
        }
        if (obj instanceof Integer) {
            return (Integer) obj;
        }
        return Integer.parseInt(String.valueOf(obj));
    };

    /**
     * Object -> Long 转换函数
     */
    public static final Function<Object, Long> TO_LONG = obj -> {
        if (Objects.isNull(obj)) {
            return null;
        }
        if (obj instanceof Long) {
            return (Long) obj;
        }
        return Long.parseLong(String.valueOf(obj));
    };

    /**
     * Object -> Boolean 转换函数
     */
    public static final Function<Object, Boolean> TO_BOOLEAN = obj -> {
        if (Objects.isNull(obj)) {
            return null;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        return Boolean.parseBoolean(String.valueOf(obj));
    };
}
