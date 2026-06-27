package io.ddd4j.core.utils;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
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
 * Json工具类
 */
@Slf4j(topic = "### BASE-CORE : JsonKit ###")
public class JsonKit {

    private JsonKit() {}

    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final ObjectMapper DEFAULT_OBJECT_MAPPER = defaultObjectMapper();

    public static ObjectMapper defaultObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.setDateFormat(new BaseSimpleDateFormat());
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
        javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(TIME_PATTERN)));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(TIME_PATTERN)));
        objectMapper.registerModule(javaTimeModule);
        objectMapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        // SimpleModule simpleModule = new SimpleModule();
        // simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
        // simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        // objectMapper.registerModule(simpleModule);
        return objectMapper;
    }

    public static ObjectMapper redisObjectMapper() {
        ObjectMapper objectMapper = defaultObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, Visibility.ANY);
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, DefaultTyping.NON_FINAL, As.WRAPPER_ARRAY);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(MapperFeature.USE_GETTERS_AS_SETTERS, false);
        objectMapper.setSerializationInclusion(Include.NON_EMPTY);
        return objectMapper;
    }

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
        // Long类型返回前端转为String类型，防止过长展示出错
        // SimpleModule simpleModule = new SimpleModule();
        // simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
        // simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        // objectMapper.registerModule(simpleModule);
        objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }

    public static String toJson(Object object) {
        if (object == null) return null;
        if (object instanceof String) return (String) object;
        try {
            return DEFAULT_OBJECT_MAPPER.writeValueAsString(object);
        } catch (IOException var2) {
            log.error("write to json string error:" + object, var2);
            return "";
        }
    }

    public Map<String, Object> toMap(String json) {
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

    public List<Map<String, Object>> toMapList(String json) {
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

    public static String toJsonWithDefaultPrettyPrinter(Object object) {
        try {
            return DEFAULT_OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (IOException e) {
            log.error("write to json string error:" + object, e);
            return "";
        }
    }

    public static <T> T toObject(Object object, Class<T> clazz) {
        if (object == null) return null;
        if (!(object instanceof String)) {
            return (T) object;
        }
        String json = (String) object;
        if (json.isEmpty()) {
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

    public static <T> T toObject(Object object, JavaType javaType) {
        if (object == null) return null;
        if (!(object instanceof String)) {
            return (T) object;
        }
        String json = (String) object;
        if (json.isEmpty()) {
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

    public static <T> List<T> toList(Object object, Class<T> beanType) {
        if (object == null) return new ArrayList<>();
        if (!(object instanceof String)) {
            return (List<T>) object;
        }
        String jsonArray = (String) object;
        if (jsonArray.isEmpty()) {
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

    public static <T> T toPojo(Object object, TypeReference<T> typeReference) {
        if (object == null) return null;
        if (!(object instanceof String)) {
            return (T) object;
        }
        String json = (String) object;
        if (json.isEmpty()) {
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

    public boolean isJson(String str) {
        return str.startsWith("{") && str.endsWith("}");
    }

    public boolean isJsonArray(String str) {
        return str.startsWith("[") && str.endsWith("]");
    }

    public static JavaType buildCollectionType(Class<? extends Collection> collectionClass, Class<?> elementClass) {
        return DEFAULT_OBJECT_MAPPER.getTypeFactory().constructCollectionType(collectionClass, elementClass);
    }

    public static JavaType buildMapType(Class<? extends Map> mapClass, Class<?> keyClass, Class<?> valueClass) {
        return DEFAULT_OBJECT_MAPPER.getTypeFactory().constructMapType(mapClass, keyClass, valueClass);
    }

    public static void update(String jsonString, Object object) {
        try {
            DEFAULT_OBJECT_MAPPER.readerForUpdating(object).readValue(jsonString);
        } catch (IOException var3) {
            log.error("update json string:" + jsonString + " to object:" + object + " error.", var3);
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