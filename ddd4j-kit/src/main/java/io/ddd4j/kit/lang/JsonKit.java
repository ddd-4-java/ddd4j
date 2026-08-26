/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.kit.lang;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Json工具类（合并 JsonKit 和 JacksonKit 功能）
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@UtilityClass
@Slf4j(topic = "### DDD4J-KIT : JsonKit ###")
public class JsonKit {

    public static final String YYYYMMDDHHMMSS = "yyyy-MM-dd HH:mm:ss";
    /**
     * Redis ObjectMapper（带 DefaultTyping，用于 Redis 序列化）
     */
    public static final ObjectMapper REDIS_OBJECT_MAPPER = redisObjectMapper();
    /**
     * TypeReference 常量
     */
    public static final TypeReference<String> STRING_TYPE = new TypeReference<String>() {
    };
    public static final TypeReference<Integer> INTEGER_TYPE = new TypeReference<Integer>() {
    };
    public static final TypeReference<Long> LONG_TYPE = new TypeReference<Long>() {
    };
    public static final TypeReference<Double> DOUBLE_TYPE = new TypeReference<Double>() {
    };
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    /**
     * 默认 ObjectMapper（用于普通 JSON 序列化）
     */
    public static final ObjectMapper DEFAULT_OBJECT_MAPPER = defaultObjectMapper();

    /**
     * 创建默认 ObjectMapper
     */
    public static ObjectMapper defaultObjectMapper() {
        // Jackson 3: JavaTimeInitializer is auto-registered; custom serializers use SimpleModule
        SimpleModule customDateModule = new SimpleModule();
        customDateModule.addSerializer(LocalDate.class,
                new com.fasterxml.jackson.databind.ext.javatime.ser.LocalDateSerializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
        customDateModule.addDeserializer(LocalDate.class,
                new com.fasterxml.jackson.databind.ext.javatime.deser.LocalDateDeserializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
        customDateModule.addSerializer(LocalDateTime.class,
                new com.fasterxml.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer(DateTimeFormatter.ofPattern(TIME_PATTERN)));
        customDateModule.addDeserializer(LocalDateTime.class,
                new com.fasterxml.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(TIME_PATTERN)));

        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .defaultDateFormat(new BaseSimpleDateFormat())
                .addModule(customDateModule)
                .changeDefaultPropertyInclusion(incl -> JsonInclude.Value.construct(Include.NON_NULL, Include.NON_NULL))
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .defaultTimeZone(TimeZone.getTimeZone("Asia/Shanghai"))
                .build();
    }

    /**
     * 创建 Redis ObjectMapper（带 DefaultTyping）
     */
    public static ObjectMapper redisObjectMapper() {
        return JsonMapper.builder()
                .activateDefaultTyping(BasicPolymorphicTypeValidator.builder().build(), DefaultTyping.NON_FINAL, As.WRAPPER_ARRAY)
                .defaultDateFormat(new SimpleDateFormat(YYYYMMDDHHMMSS))
                .changeDefaultVisibility(vis -> vis.withVisibility(PropertyAccessor.ALL, Visibility.ANY))
                .changeDefaultPropertyInclusion(incl -> JsonInclude.Value.construct(Include.NON_EMPTY, Include.NON_EMPTY))
                .configure(MapperFeature.USE_GETTERS_AS_SETTERS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    /**
     * 创建自定义 ObjectMapper
     */
    public static ObjectMapper buildObjectMapper(String datePattern, String dateTimePattern, String timePattern) {
        SimpleModule customDateModule = new SimpleModule();
        customDateModule.addSerializer(LocalDate.class,
                new com.fasterxml.jackson.databind.ext.javatime.ser.LocalDateSerializer(DateTimeFormatter.ofPattern(datePattern)));
        customDateModule.addDeserializer(LocalDate.class,
                new com.fasterxml.jackson.databind.ext.javatime.deser.LocalDateDeserializer(DateTimeFormatter.ofPattern(datePattern)));
        customDateModule.addSerializer(LocalDateTime.class,
                new com.fasterxml.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer(DateTimeFormatter.ofPattern(dateTimePattern)));
        customDateModule.addDeserializer(LocalDateTime.class,
                new com.fasterxml.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(dateTimePattern)));
        customDateModule.addSerializer(LocalTime.class,
                new com.fasterxml.jackson.databind.ext.javatime.ser.LocalTimeSerializer(DateTimeFormatter.ofPattern(timePattern)));
        customDateModule.addDeserializer(LocalTime.class,
                new com.fasterxml.jackson.databind.ext.javatime.deser.LocalTimeDeserializer(DateTimeFormatter.ofPattern(timePattern)));
        customDateModule.addSerializer(Date.class, new ValueSerializer<Date>() {
            public void serialize(Date date, JsonGenerator jsonGenerator, SerializationContext ctxt) {
                SimpleDateFormat formatter = new SimpleDateFormat(datePattern);
                String formattedDate = formatter.format(date);
                try {
                    jsonGenerator.writeString(formattedDate);
                } catch (JacksonException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        customDateModule.addDeserializer(Date.class, new ValueDeserializer<Date>() {
            public Date deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) {
                SimpleDateFormat format = new SimpleDateFormat(datePattern);
                String date = jsonParser.getText();
                try {
                    return format.parse(date);
                } catch (ParseException var6) {
                    throw new RuntimeException(var6);
                }
            }
        });
        return JsonMapper.builder()
                .addModule(customDateModule)
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    /**
     * 对象转 JSON 字符串
     */
    public static String toJson(Object object) {
        if (Objects.isNull(object)) {
            return null;
        }
        if (object instanceof String) {
            return (String) object;
        }
        try {
            return DEFAULT_OBJECT_MAPPER.writeValueAsString(object);
        } catch (JacksonException var2) {
            log.error("write to json string error:" + object, var2);
            return "";
        }
    }

    /**
     * JSON 字符串转 Map
     */
    public static Map<String, Object> toMap(String json) {
        Map<String, Object> map = new HashMap<>();
        try {
            JsonNode rootNode = DEFAULT_OBJECT_MAPPER.readTree(json);
            Iterator<String> fieldNames = rootNode.propertyNames().iterator();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode jsonNode = rootNode.get(fieldName);
                if (jsonNode.isValueNode()) {
                    map.put(fieldName, getNodeValue(jsonNode));
                } else if (jsonNode.isObject()) {
                    map.put(fieldName, toMap(jsonNode.toString()));
                } else if (jsonNode.isArray()) {
                    List<Object> list = new ArrayList<>();
                    for (JsonNode childNode : jsonNode) {
                        if (childNode.isValueNode()) {
                            list.add(getNodeValue(childNode));
                        } else if (childNode.isObject()) {
                            list.add(toMap(childNode.toString()));
                        }
                    }
                    map.put(fieldName, list);
                }
            }
        } catch (JacksonException e) {
            log.error("parse json to map error:" + json, e);
        }
        return map;
    }

    /**
     * JSON 字符串转 Map 列表
     */
    public static List<Map<String, Object>> toMapList(String json) {
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            JsonNode rootNode = DEFAULT_OBJECT_MAPPER.readTree(json);
            for (JsonNode childNode : rootNode) {
                if (childNode.isObject()) {
                    list.add(toMap(childNode.toString()));
                }
            }
            return list;
        } catch (JacksonException e) {
            log.error("parse json to map error:" + json, e);
        }
        return null;
    }

    /**
     * 对象转格式化 JSON 字符串
     */
    public static String toJsonWithDefaultPrettyPrinter(Object object) {
        try {
            return DEFAULT_OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JacksonException e) {
            log.error("write to json string error:" + object, e);
            return "";
        }
    }

    /**
     * JSON 字符串转对象
     */
    public static <T> T toObject(Object object, Class<T> clazz) {
        if (Objects.isNull(object)) {
            return null;
        }
        if (!(object instanceof String)) {
            return (T) object;
        }
        String json = (String) object;
        if (io.ddd4j.kit.lang.StrKit.isEmpty(json)) {
            return null;
        } else {
            try {
                return DEFAULT_OBJECT_MAPPER.readValue(json, clazz);
            } catch (JacksonException e) {
                log.error("parse json string error:" + json, e);
                return null;
            }
        }
    }

    /**
     * JSON 字符串转对象（带 JavaType）
     */
    public static <T> T toObject(Object object, JavaType javaType) {
        if (Objects.isNull(object)) {
            return null;
        }
        if (!(object instanceof String)) {
            return (T) object;
        }
        String json = (String) object;
        if (io.ddd4j.kit.lang.StrKit.isEmpty(json)) {
            return null;
        } else {
            try {
                return DEFAULT_OBJECT_MAPPER.readValue(json, javaType);
            } catch (JacksonException e) {
                log.error("parse json string error:" + json, e);
                return null;
            }
        }
    }

    /**
     * JSON 字符串转列表
     */
    public static <T> List<T> toList(Object object, Class<T> beanType) {
        if (Objects.isNull(object)) {
            return new ArrayList<>();
        }
        if (!(object instanceof String)) {
            return (List<T>) object;
        }
        String jsonArray = (String) object;
        if (io.ddd4j.kit.lang.StrKit.isEmpty(jsonArray)) {
            return null;
        }
        JavaType javaType = DEFAULT_OBJECT_MAPPER.getTypeFactory().constructParametricType(List.class, new Class[]{beanType});

        try {
            return DEFAULT_OBJECT_MAPPER.readValue(jsonArray, javaType);
        } catch (JacksonException e) {
            log.error("translate to POJO failed. jsonArray=" + jsonArray, e);
            return new ArrayList();
        }
    }

    /**
     * JSON 字符串转对象（带 TypeReference）
     */
    public static <T> T toPojo(Object object, TypeReference<T> typeReference) {
        if (Objects.isNull(object)) {
            return null;
        }
        if (!(object instanceof String)) {
            return (T) object;
        }
        String json = (String) object;
        if (io.ddd4j.kit.lang.StrKit.isEmpty(json)) {
            return null;
        } else {
            try {
                return DEFAULT_OBJECT_MAPPER.readValue(json, typeReference);
            } catch (JacksonException e) {
                log.error("parse json string error:" + json, e);
                return null;
            }
        }
    }

    /**
     * 判断是否为 JSON 对象字符串
     */
    public static boolean isJson(String str) {
        return str.startsWith("{") && str.endsWith("}");
    }

    /**
     * 判断是否为 JSON 数组字符串
     */
    public static boolean isJsonArray(String str) {
        return str.startsWith("[") && str.endsWith("]");
    }

    /**
     * 构建集合类型
     */
    public static JavaType buildCollectionType(Class<? extends Collection> collectionClass, Class<?> elementClass) {
        return DEFAULT_OBJECT_MAPPER.getTypeFactory().constructCollectionType(collectionClass, elementClass);
    }

    /**
     * 构建 Map 类型
     */
    public static JavaType buildMapType(Class<? extends Map> mapClass, Class<?> keyClass, Class<?> valueClass) {
        return DEFAULT_OBJECT_MAPPER.getTypeFactory().constructMapType(mapClass, keyClass, valueClass);
    }

    /**
     * 更新对象
     */
    public static void update(String jsonString, Object object) {
        try {
            DEFAULT_OBJECT_MAPPER.readerForUpdating(object).readValue(jsonString);
        } catch (JacksonException var3) {
            log.error("update json string:" + jsonString + " to object:" + object + " error.", var3);
        }
    }

    /**
     * 类型转换（合并自 JacksonKit）
     *
     * @param value     源对象
     * @param valueType 目标类型
     * @return 转换后的对象
     */
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
                return REDIS_OBJECT_MAPPER.readValue(value.toString(), valueType);
            }
            // 4、如果 value 是 其他对象类型，则使用 convertValue 方法将对象转换成 valueType 类型
            return REDIS_OBJECT_MAPPER.convertValue(value, valueType);
        } catch (JacksonException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static Object getNodeValue(JsonNode childNode) {
        return childNode.isBigDecimal() ? childNode.decimalValue() : childNode.isDouble() ? childNode.asDouble() :
                                                                     childNode.isFloat() ? childNode.floatValue() : childNode.isLong() ? childNode.asLong() :
                                                                                                                    childNode.isInt() ? childNode.asInt() : childNode.isBoolean() ? childNode.asBoolean() : childNode.asText();
    }

    private static class BaseSimpleDateFormat extends SimpleDateFormat {
        public BaseSimpleDateFormat() {
            super("yyyy-MM-dd HH:mm:ss.SSS");
        }

        public Date parse(String source) throws ParseException {
            if (source.length() == 19) {
                source = source.concat(".000");
            }

            return super.parse(source);
        }
    }

}
