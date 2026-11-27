package io.ddd4j.kit.lang;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Function;

/**
 * 通用类型转换函数常量
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@UtilityClass
public class FunctionKit {

    /** \u5c06\u975e\u7a7a\u8f93\u5165\u8f6c\u6362\u4e3a \u5e03\u5c14\u503c\uff1b\u8f93\u5165\u4e3a\u7a7a\u65f6\u8fd4\u56de null\u3002 */
    public static final Function<Object, Boolean> TO_BOOLEAN = value -> {
        if (Objects.isNull(value)) {
            return null;
        }
        return value instanceof Boolean ? (Boolean) value : Boolean.valueOf(value.toString());
    };

    /** \u5c06\u975e\u7a7a\u8f93\u5165\u8f6c\u6362\u4e3a BigDecimal \u6570\u503c\uff1b\u8f93\u5165\u4e3a\u7a7a\u65f6\u8fd4\u56de null\u3002 */
    public static final Function<Object, BigDecimal> TO_BIGDECIMAL = member -> {
        if (Objects.isNull(member)) {
            return null;
        }
        return member instanceof BigDecimal ? (BigDecimal) member : new BigDecimal(member.toString());
    };

    /** \u5c06\u975e\u7a7a\u8f93\u5165\u8f6c\u6362\u4e3a \u5b57\u8282\u503c\uff1b\u8f93\u5165\u4e3a\u7a7a\u65f6\u8fd4\u56de null\u3002 */
    public static final Function<Object, Byte> TO_BYTE = value -> {
        if (Objects.isNull(value)) {
            return null;
        }
        return value instanceof Byte ? (Byte) value : Byte.valueOf(value.toString());
    };

    /** \u5c06\u975e\u7a7a\u8f93\u5165\u8f6c\u6362\u4e3a \u5b57\u7b26\uff1b\u8f93\u5165\u4e3a\u7a7a\u65f6\u8fd4\u56de null\u3002 */
    public static final Function<Object, Character> TO_CHARACTER = value -> {
        if (Objects.isNull(value)) {
            return null;
        }
        return value instanceof Character ? (Character) value : Character.valueOf(value.toString().charAt(0));
    };

    /** \u5c06\u975e\u7a7a\u8f93\u5165\u8f6c\u6362\u4e3a \u53cc\u7cbe\u5ea6\u6570\u503c\uff1b\u8f93\u5165\u4e3a\u7a7a\u65f6\u8fd4\u56de null\u3002 */
    public static final Function<Object, Double> TO_DOUBLE = value -> {
        if (Objects.isNull(value)) {
            return null;
        }
        return value instanceof Double ? (Double) value : Double.valueOf(value.toString());
    };

    /** \u5c06\u975e\u7a7a\u8f93\u5165\u8f6c\u6362\u4e3a \u5355\u7cbe\u5ea6\u6570\u503c\uff1b\u8f93\u5165\u4e3a\u7a7a\u65f6\u8fd4\u56de null\u3002 */
    public static final Function<Object, Float> TO_FLOAT = value -> {
        if (Objects.isNull(value)) {
            return null;
        }
        return value instanceof Float ? (Float) value : Float.valueOf(value.toString());
    };

    /** \u5c06\u975e\u7a7a\u8f93\u5165\u8f6c\u6362\u4e3a \u6574\u6570\uff1b\u8f93\u5165\u4e3a\u7a7a\u65f6\u8fd4\u56de null\u3002 */
    public static final Function<Object, Integer> TO_INTEGER = value -> {
        if (Objects.isNull(value)) {
            return null;
        }
        return value instanceof Integer ? (Integer) value : Integer.valueOf(value.toString());
    };

    /** \u5c06\u975e\u7a7a\u8f93\u5165\u8f6c\u6362\u4e3a \u957f\u6574\u6570\uff1b\u8f93\u5165\u4e3a\u7a7a\u65f6\u8fd4\u56de null\u3002 */
    public static final Function<Object, Long> TO_LONG = value -> {
        if (Objects.isNull(value)) {
            return null;
        }
        return value instanceof Long ? (Long) value : Long.valueOf(value.toString());
    };

    /** \u5c06\u975e\u7a7a\u8f93\u5165\u8f6c\u6362\u4e3a \u77ed\u6574\u6570\uff1b\u8f93\u5165\u4e3a\u7a7a\u65f6\u8fd4\u56de null\u3002 */
    public static final Function<Object, Short> TO_SHORT = value -> {
        if (Objects.isNull(value)) {
            return null;
        }
        return value instanceof Short ? (Short) value : Short.valueOf(value.toString());
    };

    /** \u5c06\u975e\u7a7a\u8f93\u5165\u8f6c\u6362\u4e3a \u5b57\u7b26\u4e32\uff1b\u8f93\u5165\u4e3a\u7a7a\u65f6\u8fd4\u56de null\u3002 */
    public static final Function<Object, String> TO_STRING = value -> Objects.toString(value, null);

}
