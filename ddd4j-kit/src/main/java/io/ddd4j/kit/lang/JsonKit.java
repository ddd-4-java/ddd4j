package io.ddd4j.kit.lang;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
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

    /** \u65e5\u671f\u65f6\u95f4\u683c\u5f0f\u6a21\u677f\u3002 */
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
    /** \u6574\u6570 JSON \u7c7b\u578b\u5f15\u7528\u3002 */
    public static final TypeReference<Integer> INTEGER_TYPE = new TypeReference<Integer>() {
    };
    /** \u957f\u6574\u6570 JSON \u7c7b\u578b\u5f15\u7528\u3002 */
    public static final TypeReference<Long> LONG_TYPE = new TypeReference<Long>() {
    };
    /** \u53cc\u7cbe\u5ea6 JSON \u7c7b\u578b\u5f15\u7528\u3002 */
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
     * \u521b\u5efa\u9ed8\u8ba4 ObjectMapper
     *
     * @return \u5b8c\u6210\u65e5\u671f\u4e0e\u65f6\u95f4\u7c7b\u578b\u5e8f\u5217\u5316\u914d\u7f6e\u7684 ObjectMapper \u5b9e\u4f8b
     */
    public static ObjectMapper defaultObjectMapper() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
        javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(TIME_PATTERN)));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(TIME_PATTERN)));
        ObjectMapper objectMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .defaultDateFormat(new BaseSimpleDateFormat())
                .addModule(javaTimeModule)
                .defaultPropertyInclusion(JsonInclude.Value.construct(Include.NON_NULL, Include.NON_NULL))
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .build();
        objectMapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return objectMapper;
    }

    /**
     * \u521b\u5efa Redis ObjectMapper\uff08\u5e26 DefaultTyping\uff09
     *
     * @return \u542f\u7528\u9ed8\u8ba4\u7c7b\u578b\u4fe1\u606f\uff08DefaultTyping\uff09\u7684 ObjectMapper \u5b9e\u4f8b
     */
    public static ObjectMapper redisObjectMapper() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .activateDefaultTyping(LaissezFaireSubTypeValidator.instance, DefaultTyping.NON_FINAL, As.WRAPPER_ARRAY)
                .defaultDateFormat(new SimpleDateFormat(YYYYMMDDHHMMSS))
                .visibility(PropertyAccessor.ALL, Visibility.ANY)
                .defaultPropertyInclusion(JsonInclude.Value.construct(Include.NON_EMPTY, Include.NON_EMPTY))
                .configure(MapperFeature.USE_GETTERS_AS_SETTERS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
        return objectMapper;
    }

    /**
     * \u521b\u5efa\u81ea\u5b9a\u4e49 ObjectMapper
     *
     * @param datePattern \u65e5\u671f\u683c\u5f0f\u6a21\u5f0f\u4e32
     * @param dateTimePattern \u65e5\u671f\u65f6\u95f4\u683c\u5f0f\u6a21\u5f0f\u4e32
     * @param timePattern \u65f6\u95f4\u683c\u5f0f\u6a21\u5f0f\u4e32
     * @return \u6309\u6307\u5b9a\u6a21\u5f0f\u5e8f\u5217\u5316\u65f6\u95f4\u7c7b\u578b\u7684 ObjectMapper \u5b9e\u4f8b
     */
    public static ObjectMapper buildObjectMapper(String datePattern, String dateTimePattern, String timePattern) {
        ObjectMapper objectMapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ofPattern(datePattern)));
        javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern(datePattern)));
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(dateTimePattern)));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(dateTimePattern)));
        javaTimeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern(timePattern)));
        javaTimeModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(DateTimeFormatter.ofPattern(timePattern)));
        javaTimeModule.addSerializer(Date.class, new JsonSerializer<Date>() {
            public void serialize(Date date, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                SimpleDateFormat formatter = new SimpleDateFormat(datePattern);
                String formattedDate = formatter.format(date);
                jsonGenerator.writeString(formattedDate);
            }
        });
        javaTimeModule.addDeserializer(Date.class, new JsonDeserializer<Date>() {
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
        objectMapper.registerModule(javaTimeModule);
        objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }

    /**
     * \u5bf9\u8c61\u8f6c JSON \u5b57\u7b26\u4e32
     *
     * @param object \u5f85\u5e8f\u5217\u5316\u7684\u5bf9\u8c61
     * @return JSON \u5b57\u7b26\u4e32\uff1b\u5bf9\u8c61\u4e3a null \u65f6\u8fd4\u56de null\uff0c\u5e8f\u5217\u5316\u5931\u8d25\u65f6\u8fd4\u56de\u7a7a\u5b57\u7b26\u4e32
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
        } catch (IOException var2) {
            log.error("write to json string error:" + object, var2);
            return "";
        }
    }

    /**
     * JSON \u5b57\u7b26\u4e32\u8f6c Map
     *
     * @param json JSON \u5bf9\u8c61\u5b57\u7b26\u4e32
     * @return \u89e3\u6790\u5f97\u5230\u7684\u952e\u503c\u5bf9 Map\uff1b\u89e3\u6790\u5931\u8d25\u65f6\u8fd4\u56de\u7a7a Map
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
        } catch (JsonProcessingException e) {
            log.error("parse json to map error:" + json, e);
        }
        return map;
    }

    /**
     * JSON \u5b57\u7b26\u4e32\u8f6c Map \u5217\u8868
     *
     * @param json JSON \u6570\u7ec4\u5b57\u7b26\u4e32
     * @return \u7531\u5404\u5bf9\u8c61\u5143\u7d20\u7ec4\u6210\u7684 Map \u5217\u8868\uff1b\u89e3\u6790\u5931\u8d25\u65f6\u8fd4\u56de null
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
        } catch (JsonProcessingException e) {
            log.error("parse json to map error:" + json, e);
        }
        return null;
    }

    /**
     * \u5bf9\u8c61\u8f6c\u683c\u5f0f\u5316 JSON \u5b57\u7b26\u4e32
     *
     * @param object \u5f85\u5e8f\u5217\u5316\u7684\u5bf9\u8c61
     * @return \u5e26\u7f29\u8fdb\u683c\u5f0f\u7684 JSON \u5b57\u7b26\u4e32\uff1b\u5e8f\u5217\u5316\u5931\u8d25\u65f6\u8fd4\u56de\u7a7a\u5b57\u7b26\u4e32
     */
    public static String toJsonWithDefaultPrettyPrinter(Object object) {
        try {
            return DEFAULT_OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (IOException e) {
            log.error("write to json string error:" + object, e);
            return "";
        }
    }

    /**
     * JSON \u5b57\u7b26\u4e32\u8f6c\u5bf9\u8c61
     *
     * @param object JSON \u5b57\u7b26\u4e32\u6216\u5df2\u8f6c\u6362\u7684\u76ee\u6807\u5bf9\u8c61
     * @param clazz \u76ee\u6807\u7c7b\u578b
     * @param <T> \u76ee\u6807\u7c7b\u578b
     * @return \u8f6c\u6362\u540e\u7684\u5bf9\u8c61\uff1b\u8f93\u5165\u4e3a\u7a7a\u3001\u7a7a\u767d\u6216\u89e3\u6790\u5931\u8d25\u65f6\u8fd4\u56de null
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
            } catch (IOException e) {
                log.error("parse json string error:" + json, e);
                return null;
            }
        }
    }

    /**
     * JSON \u5b57\u7b26\u4e32\u8f6c\u5bf9\u8c61\uff08\u5e26 JavaType\uff09
     *
     * @param object JSON \u5b57\u7b26\u4e32\u6216\u5df2\u8f6c\u6362\u7684\u76ee\u6807\u5bf9\u8c61
     * @param javaType \u76ee\u6807 Java \u7c7b\u578b
     * @param <T> \u76ee\u6807\u7c7b\u578b
     * @return \u8f6c\u6362\u540e\u7684\u5bf9\u8c61\uff1b\u8f93\u5165\u4e3a\u7a7a\u3001\u7a7a\u767d\u6216\u89e3\u6790\u5931\u8d25\u65f6\u8fd4\u56de null
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
            } catch (IOException e) {
                log.error("parse json string error:" + json, e);
                return null;
            }
        }
    }

    /**
     * JSON \u5b57\u7b26\u4e32\u8f6c\u5217\u8868
     *
     * @param object JSON \u6570\u7ec4\u5b57\u7b26\u4e32\u6216\u5df2\u8f6c\u6362\u7684\u5217\u8868
     * @param beanType \u5217\u8868\u5143\u7d20\u7c7b\u578b
     * @param <T> \u5217\u8868\u5143\u7d20\u7c7b\u578b
     * @return \u8f6c\u6362\u540e\u7684\u5217\u8868\uff1b\u8f93\u5165\u4e3a null \u65f6\u8fd4\u56de\u7a7a\u5217\u8868\uff0c\u4e3a\u7a7a\u767d\u5b57\u7b26\u4e32\u65f6\u8fd4\u56de null
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
        } catch (IOException e) {
            log.error("translate to POJO failed. jsonArray=" + jsonArray, e);
            return new ArrayList();
        }
    }

    /**
     * JSON \u5b57\u7b26\u4e32\u8f6c\u5bf9\u8c61\uff08\u5e26 TypeReference\uff09
     *
     * @param object JSON \u5b57\u7b26\u4e32\u6216\u5df2\u8f6c\u6362\u7684\u76ee\u6807\u5bf9\u8c61
     * @param typeReference \u76ee\u6807\u7c7b\u578b\u5f15\u7528
     * @param <T> \u76ee\u6807\u7c7b\u578b
     * @return \u8f6c\u6362\u540e\u7684\u5bf9\u8c61\uff1b\u8f93\u5165\u4e3a\u7a7a\u3001\u7a7a\u767d\u6216\u89e3\u6790\u5931\u8d25\u65f6\u8fd4\u56de null
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
            } catch (IOException e) {
                log.error("parse json string error:" + json, e);
                return null;
            }
        }
    }

    /**
     * \u5224\u65ad\u662f\u5426\u4e3a JSON \u5bf9\u8c61\u5b57\u7b26\u4e32
     *
     * @param str \u5f85\u6821\u9a8c\u7684\u5b57\u7b26\u4e32
     * @return \u5b57\u7b26\u4e32\u4ee5\u82b1\u62ec\u53f7\u5305\u88f9\uff08JSON \u5bf9\u8c61\u5f62\u5f0f\uff09\u65f6\u8fd4\u56de true\uff0c\u5426\u5219\u8fd4\u56de false
     */
    public static boolean isJson(String str) {
        return str.startsWith("{") && str.endsWith("}");
    }

    /**
     * \u5224\u65ad\u662f\u5426\u4e3a JSON \u6570\u7ec4\u5b57\u7b26\u4e32
     *
     * @param str \u5f85\u6821\u9a8c\u7684\u5b57\u7b26\u4e32
     * @return \u5b57\u7b26\u4e32\u4ee5\u65b9\u62ec\u53f7\u5305\u88f9\uff08JSON \u6570\u7ec4\u5f62\u5f0f\uff09\u65f6\u8fd4\u56de true\uff0c\u5426\u5219\u8fd4\u56de false
     */
    public static boolean isJsonArray(String str) {
        return str.startsWith("[") && str.endsWith("]");
    }

    /**
     * \u6784\u5efa\u96c6\u5408\u7c7b\u578b
     *
     * @param collectionClass \u96c6\u5408\u7c7b\u578b
     * @param elementClass \u5143\u7d20\u7c7b\u578b
     * @return \u5bf9\u5e94\u7684\u96c6\u5408 JavaType
     */
    public static JavaType buildCollectionType(Class<? extends Collection> collectionClass, Class<?> elementClass) {
        return DEFAULT_OBJECT_MAPPER.getTypeFactory().constructCollectionType(collectionClass, elementClass);
    }

    /**
     * \u6784\u5efa Map \u7c7b\u578b
     *
     * @param mapClass Map \u7c7b\u578b
     * @param keyClass \u952e\u7c7b\u578b
     * @param valueClass \u503c\u7c7b\u578b
     * @return \u5bf9\u5e94\u7684\u6620\u5c04 JavaType
     */
    public static JavaType buildMapType(Class<? extends Map> mapClass, Class<?> keyClass, Class<?> valueClass) {
        return DEFAULT_OBJECT_MAPPER.getTypeFactory().constructMapType(mapClass, keyClass, valueClass);
    }

    /**
     * \u66f4\u65b0\u5bf9\u8c61
     *
     * @param jsonString \u5305\u542b\u5f85\u66f4\u65b0\u5b57\u6bb5\u7684 JSON \u5b57\u7b26\u4e32
     * @param object \u5f85\u66f4\u65b0\u7684\u76ee\u6807\u5bf9\u8c61
     */
    public static void update(String jsonString, Object object) {
        try {
            DEFAULT_OBJECT_MAPPER.readerForUpdating(object).readValue(jsonString);
        } catch (IOException var3) {
            log.error("update json string:" + jsonString + " to object:" + object + " error.", var3);
        }
    }

    /**
     * \u7c7b\u578b\u8f6c\u6362\uff08\u5408\u5e76\u81ea JacksonKit\uff09
     * @param value \u6e90\u5bf9\u8c61
     * @param valueType \u76ee\u6807\u7c7b\u578b
     * @return \u8f6c\u6362\u540e\u7684\u5bf9\u8c61
     *
     * @param <T> \u76ee\u6807\u7c7b\u578b
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
        } catch (JsonProcessingException e) {
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
