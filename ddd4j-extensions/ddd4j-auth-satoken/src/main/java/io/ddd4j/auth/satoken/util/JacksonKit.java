package io.ddd4j.auth.satoken.util;

import cn.dev33.satoken.exception.SaTokenException;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;

import java.text.SimpleDateFormat;
import java.util.Objects;

public class JacksonKit {

    public static final String YYYYMMDDHHMMSS = "yyyy-MM-dd HH:mm:ss";

    // 单独初始化ObjectMapper，不使用全局对象，因为下面要指定特殊的输出处理，会影响内部业务逻辑
    public static ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            // 指定序列化输入的类型，类必须是非final修饰的，final修饰的类，比如String,Integer等会跑出异常
            .activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL)
            .defaultDateFormat(new SimpleDateFormat(YYYYMMDDHHMMSS))
            // 指定要序列化的域，field,get和set,以及修饰符范围，ANY是都有包括private和public
            .visibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();
    ;

    public static final StringTypeReference STRING_TYPE = new StringTypeReference();

    public static final IntegerTypeReference INTEGER_TYPE = new IntegerTypeReference();

    public static final LongTypeReference LONG_TYPE = new LongTypeReference();

    public static final DoubleTypeReference DOUBLE_TYPE = new DoubleTypeReference();

    public static class StringTypeReference extends TypeReference<String> {

    }

    public static class IntegerTypeReference extends TypeReference<Integer> {

    }

    public static class LongTypeReference extends TypeReference<Long> {

    }

    public static class DoubleTypeReference extends TypeReference<Double> {

    }

    public static <T> T toType(Object value, Class<T> valueType) {
        // 1、如果value为空，直接返回null
        if (Objects.isNull(value)) {
            return null;
        }
        // 2、如果value的类型和valueType一致，直接返回value
        if (value.getClass().isAssignableFrom(valueType)) {
            return valueType.cast(value);
        }
        try {
            // 3、如果 value 是 String 类型，则使用 readValue 方法将字符串转换成 valueType 类型
            if (value instanceof String) {
                return OBJECT_MAPPER.readValue(value.toString(), valueType);
            }
            // 4、如果 value 是 其他对象类型，则使用 convertValue 方法将对象转换成 valueType 类型
            return OBJECT_MAPPER.convertValue(value, valueType);
        } catch (JsonProcessingException e) {
            throw new SaTokenException(e.getMessage());
        }
    }

}
