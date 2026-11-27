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
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
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
import java.time.*;
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

    /**
     * 日期时间格式模板。
     */

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
    /**
     * 整数 JSON 类型引用。
     */
    public static final TypeReference<Integer> INTEGER_TYPE = new TypeReference<Integer>() {
    };
    /**
     * 长整数 JSON 类型引用。
     */
    public static final TypeReference<Long> LONG_TYPE = new TypeReference<Long>() {
    };
    /**
     * 双精度 JSON 类型引用。
     */
    public static final TypeReference<Double> DOUBLE_TYPE = new TypeReference<Double>() {
    };
    /** \u53ea\u542b\u5e74\u6708\u65e5\u7684\u517c\u5bb9\u65e5\u671f\u683c\u5f0f\u3002 */
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    /** \u542b\u5e74\u6708\u65e5\u53ca\u79d2\u7684\u517c\u5bb9\u65e5\u671f\u65f6\u95f4\u683c\u5f0f\u3002 */
    private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    /**
     * 默认 ObjectMapper（用于普通 JSON 序列化）
     */
    public static final ObjectMapper DEFAULT_OBJECT_MAPPER = defaultObjectMapper();

    /**
     * 创建默认 ObjectMapper
     *
     * @return 完成日期与时间类型序列化配置的 ObjectMapper 实例
     */
    public static ObjectMapper defaultObjectMapper() {
        // Jackson 2: java.time 序列化通过 SimpleModule + JsonSerializer/JsonDeserializer 注册（jsr310 未在 classpath）
        SimpleModule customDateModule = new SimpleModule();
        registerIsoTimeTypes(customDateModule);
        customDateModule.addSerializer(LocalDate.class, localDateSerializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
        customDateModule.addDeserializer(LocalDate.class, localDateDeserializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
        customDateModule.addSerializer(LocalDateTime.class, localDateTimeSerializer(DateTimeFormatter.ofPattern(TIME_PATTERN)));
        customDateModule.addDeserializer(LocalDateTime.class, localDateTimeDeserializer(DateTimeFormatter.ofPattern(TIME_PATTERN)));

        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .defaultDateFormat(new BaseSimpleDateFormat())
                .addModule(customDateModule)
                .defaultPropertyInclusion(JsonInclude.Value.construct(Include.NON_NULL, Include.NON_NULL))
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .defaultTimeZone(TimeZone.getTimeZone("Asia/Shanghai"))
                .build();
    }

    /**
     * 创建 Redis ObjectMapper（带 DefaultTyping）
     *
     * @return 启用默认类型信息（DefaultTyping）的 ObjectMapper 实例
     */
    public static ObjectMapper redisObjectMapper() {
        return JsonMapper.builder()
                .activateDefaultTyping(BasicPolymorphicTypeValidator.builder().build(), ObjectMapper.DefaultTyping.NON_FINAL, As.WRAPPER_ARRAY)
                .defaultDateFormat(new SimpleDateFormat(YYYYMMDDHHMMSS))
                .visibility(PropertyAccessor.ALL, Visibility.ANY)
                .defaultPropertyInclusion(JsonInclude.Value.construct(Include.NON_EMPTY, Include.NON_EMPTY))
                .configure(MapperFeature.USE_GETTERS_AS_SETTERS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    /**
     * 创建自定义 ObjectMapper
     *
     * @param datePattern     日期格式模式串
     * @param dateTimePattern 日期时间格式模式串
     * @param timePattern     时间格式模式串
     * @return 按指定模式序列化时间类型的 ObjectMapper 实例
     */
    public static ObjectMapper buildObjectMapper(String datePattern, String dateTimePattern, String timePattern) {
        SimpleModule customDateModule = new SimpleModule();
        registerIsoTimeTypes(customDateModule);
        customDateModule.addSerializer(LocalDate.class, localDateSerializer(DateTimeFormatter.ofPattern(datePattern)));
        customDateModule.addDeserializer(LocalDate.class, localDateDeserializer(DateTimeFormatter.ofPattern(datePattern)));
        customDateModule.addSerializer(LocalDateTime.class, localDateTimeSerializer(DateTimeFormatter.ofPattern(dateTimePattern)));
        customDateModule.addDeserializer(LocalDateTime.class, localDateTimeDeserializer(DateTimeFormatter.ofPattern(dateTimePattern)));
        customDateModule.addSerializer(LocalTime.class, localTimeSerializer(DateTimeFormatter.ofPattern(timePattern)));
        customDateModule.addDeserializer(LocalTime.class, localTimeDeserializer(DateTimeFormatter.ofPattern(timePattern)));
        customDateModule.addSerializer(Date.class, new JsonSerializer<Date>() {
            public void serialize(Date date, JsonGenerator jsonGenerator, SerializerProvider ctxt) throws IOException {
                SimpleDateFormat formatter = new SimpleDateFormat(datePattern);
                String formattedDate = formatter.format(date);
                try {
                    jsonGenerator.writeString(formattedDate);
                } catch (JacksonException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        customDateModule.addDeserializer(Date.class, new JsonDeserializer<Date>() {
            public Date deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
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
     *
     * @param object 待序列化的对象
     * @return JSON 字符串；对象为 null 时返回 null，序列化失败时返回空字符串
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
     *
     * @param json JSON 对象字符串
     * @return 解析得到的键值对 Map；解析失败时返回空 Map
     */
    public static Map<String, Object> toMap(String json) {
        Map<String, Object> map = new HashMap<>();
        try {
            JsonNode rootNode = DEFAULT_OBJECT_MAPPER.readTree(json);
            Iterator<String> fieldNames = rootNode.fieldNames();
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
     *
     * @param json JSON 数组字符串
     * @return 由各对象元素组成的 Map 列表；解析失败时返回 null
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
     *
     * @param object 待序列化的对象
     * @return 带缩进格式的 JSON 字符串；序列化失败时返回空字符串
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
     *
     * @param object JSON 字符串或已转换的目标对象
     * @param clazz  目标类型
     * @param <T>    目标类型
     * @return 转换后的对象；输入为空、空白或解析失败时返回 null
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
     *
     * @param object   JSON 字符串或已转换的目标对象
     * @param javaType 目标 Java 类型
     * @param <T>      目标类型
     * @return 转换后的对象；输入为空、空白或解析失败时返回 null
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
     *
     * @param object   JSON 数组字符串或已转换的列表
     * @param beanType 列表元素类型
     * @param <T>      列表元素类型
     * @return 转换后的列表；输入为 null 时返回空列表，为空白字符串时返回 null
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
     *
     * @param object        JSON 字符串或已转换的目标对象
     * @param typeReference 目标类型引用
     * @param <T>           目标类型
     * @return 转换后的对象；输入为空、空白或解析失败时返回 null
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
     *
     * @param str 待校验的字符串
     * @return 字符串以花括号包裹（JSON 对象形式）时返回 true，否则返回 false
     */
    public static boolean isJson(String str) {
        return str.startsWith("{") && str.endsWith("}");
    }

    /**
     * 判断是否为 JSON 数组字符串
     *
     * @param str 待校验的字符串
     * @return 字符串以方括号包裹（JSON 数组形式）时返回 true，否则返回 false
     */
    public static boolean isJsonArray(String str) {
        return str.startsWith("[") && str.endsWith("]");
    }

    /**
     * 构建集合类型
     *
     * @param collectionClass 集合类型
     * @param elementClass    元素类型
     * @return 对应的集合 JavaType
     */
    public static JavaType buildCollectionType(Class<? extends Collection> collectionClass, Class<?> elementClass) {
        return DEFAULT_OBJECT_MAPPER.getTypeFactory().constructCollectionType(collectionClass, elementClass);
    }

    /**
     * 构建 Map 类型
     *
     * @param mapClass   Map 类型
     * @param keyClass   键类型
     * @param valueClass 值类型
     * @return 对应的映射 JavaType
     */
    public static JavaType buildMapType(Class<? extends Map> mapClass, Class<?> keyClass, Class<?> valueClass) {
        return DEFAULT_OBJECT_MAPPER.getTypeFactory().constructMapType(mapClass, keyClass, valueClass);
    }

    /**
     * 更新对象
     *
     * @param jsonString 包含待更新字段的 JSON 字符串
     * @param object     待更新的目标对象
     */
    public static void update(String jsonString, Object object) {
        try {
            DEFAULT_OBJECT_MAPPER.readerForUpdating(object).readValue(jsonString);
        } catch (JacksonException var3) {
            log.error("update json string:" + jsonString + " to object:" + object + " error.", var3);
        }
    }

    /**
     * 将任意值转换为目标类型
     *
     * @param value     源对象
     * @param valueType 目标类型
     * @param <T>       目标类型
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

    /**
     * \u6309 JSON \u6570\u5b57\u3001\u5e03\u5c14\u3001\u6587\u672c\u6216\u5176\u4ed6\u8282\u70b9\u7c7b\u578b\u53d6\u51fa Java \u503c\u3002
     * @param childNode \u5f85\u8f6c\u6362\u7684 Jackson \u6811\u8282\u70b9
     * @return JSON \u8282\u70b9\u5bf9\u5e94\u7684 Java \u503c
     */
    private static Object getNodeValue(JsonNode childNode) {
        return childNode.isBigDecimal() ? childNode.decimalValue() : childNode.isDouble() ? childNode.asDouble() :
                childNode.isFloat() ? childNode.floatValue() : childNode.isLong() ? childNode.asLong() :
                        childNode.isInt() ? childNode.asInt() : childNode.isBoolean() ? childNode.asBoolean() : childNode.asText();
    }

    /**
     * \u4e3a Jackson \u6a21\u5757\u6ce8\u518c ISO \u65f6\u95f4\u7c7b\u578b\u7684\u5e8f\u5217\u5316\u53ca\u53cd\u5e8f\u5217\u5316\u5668\u3002
     * @param module \u63a5\u6536\u65f6\u95f4\u7c7b\u578b\u5904\u7406\u5668\u7684 Jackson \u6a21\u5757
     */
    private static void registerIsoTimeTypes(SimpleModule module) {
        module.addSerializer(Instant.class, new JsonSerializer<Instant>() {
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.toString());
            }
        });
        module.addDeserializer(Instant.class, new JsonDeserializer<Instant>() {
            public Instant deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
                return Instant.parse(parser.getText());
            }
        });
        module.addSerializer(Duration.class, new JsonSerializer<Duration>() {
            public void serialize(Duration value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.toString());
            }
        });
        module.addDeserializer(Duration.class, new JsonDeserializer<Duration>() {
            public Duration deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
                return Duration.parse(parser.getText());
            }
        });
    }

    /**
     * \u7528\u7ed9\u5b9a\u683c\u5f0f\u8f93\u51fa\u65e5\u671f\u7684 JSON \u5b57\u7b26\u4e32\u3002
     * @param formatter \u5e8f\u5217\u5316\u4f7f\u7528\u7684 Java \u65f6\u95f4\u683c\u5f0f
     * @return \u9002\u7528\u4e8e\u8be5\u65f6\u95f4\u7c7b\u578b\u7684 Jackson \u5e8f\u5217\u5316\u5668
     */
    private static JsonSerializer<LocalDate> localDateSerializer(DateTimeFormatter formatter) {
        return new JsonSerializer<LocalDate>() {
            public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.format(formatter));
            }
        };
    }

    /**
     * \u6309\u7ed9\u5b9a\u683c\u5f0f\u89e3\u6790 JSON \u4e2d\u7684\u65e5\u671f\u3002
     * @param formatter \u53cd\u5e8f\u5217\u5316\u4f7f\u7528\u7684 Java \u65f6\u95f4\u683c\u5f0f
     * @return \u9002\u7528\u4e8e\u8be5\u65f6\u95f4\u7c7b\u578b\u7684 Jackson \u53cd\u5e8f\u5217\u5316\u5668
     */
    private static JsonDeserializer<LocalDate> localDateDeserializer(DateTimeFormatter formatter) {
        return new JsonDeserializer<LocalDate>() {
            public LocalDate deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
                return LocalDate.parse(parser.getText(), formatter);
            }
        };
    }

    /**
     * \u7528\u7ed9\u5b9a\u683c\u5f0f\u8f93\u51fa\u672c\u5730\u65e5\u671f\u65f6\u95f4\u7684 JSON \u5b57\u7b26\u4e32\u3002
     * @param formatter \u5e8f\u5217\u5316\u4f7f\u7528\u7684 Java \u65f6\u95f4\u683c\u5f0f
     * @return \u9002\u7528\u4e8e\u8be5\u65f6\u95f4\u7c7b\u578b\u7684 Jackson \u5e8f\u5217\u5316\u5668
     */
    private static JsonSerializer<LocalDateTime> localDateTimeSerializer(DateTimeFormatter formatter) {
        return new JsonSerializer<LocalDateTime>() {
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.format(formatter));
            }
        };
    }

    /**
     * \u6309\u7ed9\u5b9a\u683c\u5f0f\u89e3\u6790 JSON \u4e2d\u7684\u672c\u5730\u65e5\u671f\u65f6\u95f4\u3002
     * @param formatter \u53cd\u5e8f\u5217\u5316\u4f7f\u7528\u7684 Java \u65f6\u95f4\u683c\u5f0f
     * @return \u9002\u7528\u4e8e\u8be5\u65f6\u95f4\u7c7b\u578b\u7684 Jackson \u53cd\u5e8f\u5217\u5316\u5668
     */
    private static JsonDeserializer<LocalDateTime> localDateTimeDeserializer(DateTimeFormatter formatter) {
        return new JsonDeserializer<LocalDateTime>() {
            public LocalDateTime deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
                return LocalDateTime.parse(parser.getText(), formatter);
            }
        };
    }

    /**
     * \u7528\u7ed9\u5b9a\u683c\u5f0f\u8f93\u51fa\u672c\u5730\u65f6\u95f4\u7684 JSON \u5b57\u7b26\u4e32\u3002
     * @param formatter \u5e8f\u5217\u5316\u4f7f\u7528\u7684 Java \u65f6\u95f4\u683c\u5f0f
     * @return \u9002\u7528\u4e8e\u8be5\u65f6\u95f4\u7c7b\u578b\u7684 Jackson \u5e8f\u5217\u5316\u5668
     */
    private static JsonSerializer<LocalTime> localTimeSerializer(DateTimeFormatter formatter) {
        return new JsonSerializer<LocalTime>() {
            public void serialize(LocalTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.format(formatter));
            }
        };
    }

    /**
     * \u6309\u7ed9\u5b9a\u683c\u5f0f\u89e3\u6790 JSON \u4e2d\u7684\u672c\u5730\u65f6\u95f4\u3002
     * @param formatter \u53cd\u5e8f\u5217\u5316\u4f7f\u7528\u7684 Java \u65f6\u95f4\u683c\u5f0f
     * @return \u9002\u7528\u4e8e\u8be5\u65f6\u95f4\u7c7b\u578b\u7684 Jackson \u53cd\u5e8f\u5217\u5316\u5668
     */
    private static JsonDeserializer<LocalTime> localTimeDeserializer(DateTimeFormatter formatter) {
        return new JsonDeserializer<LocalTime>() {
            public LocalTime deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
                return LocalTime.parse(parser.getText(), formatter);
            }
        };
    }

    /** \u89e3\u6790\u79d2\u7ea7\u548c\u6beb\u79d2\u7ea7\u65f6\u95f4\u6587\u672c\uff0c\u7f3a\u5c11\u5c0f\u6570\u90e8\u5206\u65f6\u8865\u9f50\u6beb\u79d2\u3002 */
    private static class BaseSimpleDateFormat extends SimpleDateFormat {
        /** \u4f7f\u7528\u5e26\u6beb\u79d2\u7684\u65e5\u671f\u65f6\u95f4\u6a21\u5f0f\u521d\u59cb\u5316\u683c\u5f0f\u5668\u3002 */
        public BaseSimpleDateFormat() {
            super("yyyy-MM-dd HH:mm:ss.SSS");
        }

        /**
         * \u5728\u7f3a\u5c11\u6beb\u79d2\u540e\u7f00\u7684\u79d2\u7ea7\u6587\u672c\u4e0a\u8865\u96f6\uff0c\u518d\u4ea4\u7ed9\u65e5\u671f\u65f6\u95f4\u683c\u5f0f\u5668\u89e3\u6790\u3002
         * @param source \u79d2\u7ea7\u6216\u6beb\u79d2\u7ea7\u65e5\u671f\u65f6\u95f4\u6587\u672c
         * @return \u89e3\u6790\u540e\u7684\u65e5\u671f
         * @throws ParseException \u6587\u672c\u4e0d\u80fd\u6309\u65e5\u671f\u65f6\u95f4\u6a21\u5f0f\u89e3\u6790
         */
        public Date parse(String source) throws ParseException {
            if (source.length() == 19) {
                source = source.concat(".000");
            }

            return super.parse(source);
        }
    }

}
