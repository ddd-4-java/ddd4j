package io.ddd4j.data.mybatis.typehandler;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.extension.jackson.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

/**
 * JacksonTypeHandler
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@MappedTypes({List.class})
@MappedJdbcTypes(JdbcType.VARCHAR)
public class MyJacksonTypeHandler extends AbstractJsonTypeHandler<Object> {

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        OBJECT_MAPPER.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        OBJECT_MAPPER.setDateFormat(DateUtil.newSimpleFormat("yyyy-MM-dd HH:mm:ss"));
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        OBJECT_MAPPER.registerModule(javaTimeModule);
    }

    private final Class<?> type;

    public MyJacksonTypeHandler(Class<?> type) {
        super(type);
        if (log.isTraceEnabled()) {
            log.trace("MyJacksonTypeHandler(" + type + ")");
        }
        Objects.requireNonNull(type, "Type argument cannot be null");
        this.type = type;
    }

    @Override
    public Object parse(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}