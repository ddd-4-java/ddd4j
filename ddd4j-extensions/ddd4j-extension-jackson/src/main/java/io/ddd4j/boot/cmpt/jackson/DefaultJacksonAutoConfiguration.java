package io.ddd4j.boot.cmpt.jackson;

import cn.hutool.core.date.DatePattern;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import hitool.core.lang3.time.DateFormats;
import io.ddd4j.boot.cmpt.jackson.ser.MyBeanSerializerModifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson ObjectMapper 默认配置（纯 Spring Framework）。
 */
@Configuration(proxyBeanMethods = false)
public class DefaultJacksonAutoConfiguration {

    @Value("${spring.jackson.default-null-array-serializer:true}")
    private boolean defaultNullArraySerializer;
    @Value("${spring.jackson.default-null-number-serializer:false}")
    private boolean defaultNullNumberSerializer;
    @Value("${spring.jackson.default-null-string-serializer:true}")
    private boolean defaultNullStringSerializer;
    @Value("${spring.jackson.default-null-date-serializer:true}")
    private boolean defaultNullDateSerializer;
    @Value("${spring.jackson.default-null-boolean-serializer:false}")
    private boolean defaultNullBooleanSerializer;
    @Value("${spring.jackson.default-null-json-object-serializer:true}")
    private boolean defaultNullJsonObjectSerializer;

    /**
     * 注册主 ObjectMapper Bean。
     */
    @Bean
    @Order(Integer.MIN_VALUE)
    @Primary
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder
                .simpleDateFormat(DateFormats.DATE_LONGFORMAT)
                .failOnEmptyBeans(false)
                .failOnUnknownProperties(false)
                .featuresToEnable(MapperFeature.USE_GETTERS_AS_SETTERS, MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
                .createXmlMapper(false)
                .build();
        JavaTimeModule module = new JavaTimeModule();
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        module.addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ofPattern(DatePattern.NORM_DATE_PATTERN)));
        module.addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern(DatePattern.NORM_TIME_PATTERN)));
        objectMapper.registerModule(module);
        MyBeanSerializerModifier myBeanSerializerModifier = new MyBeanSerializerModifier(defaultNullArraySerializer,
                defaultNullNumberSerializer, defaultNullStringSerializer,
                defaultNullDateSerializer, defaultNullBooleanSerializer, defaultNullJsonObjectSerializer);
        objectMapper.setSerializerFactory(objectMapper.getSerializerFactory().withSerializerModifier(myBeanSerializerModifier));
        return objectMapper;
    }

}
