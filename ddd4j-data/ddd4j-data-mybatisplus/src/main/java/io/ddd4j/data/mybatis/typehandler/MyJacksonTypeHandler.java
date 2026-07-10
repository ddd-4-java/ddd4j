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
 * Jackson JSON 类型处理器：基于 Jackson 实现 JSON 字符串与 Java 对象的互转。
 *
 * <p>继承 MyBatis-Plus {@link AbstractJsonTypeHandler}，使用自定义 {@link ObjectMapper} 配置
 * （时区 GMT+8、日期格式 {@code yyyy-MM-dd HH:mm:ss}、忽略 null 值）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@MappedTypes({List.class})
@MappedJdbcTypes(JdbcType.VARCHAR)
public class MyJacksonTypeHandler extends AbstractJsonTypeHandler<Object> {

    /**
     * Jackson ObjectMapper 实例（静态共享，已配置时区/日期/忽略 null）
     */
    private static final ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();
        OBJECT_MAPPER.setDefaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL));
        OBJECT_MAPPER.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        OBJECT_MAPPER.setDateFormat(DateUtil.newSimpleFormat("yyyy-MM-dd HH:mm:ss"));
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        OBJECT_MAPPER.registerModule(javaTimeModule);
    }

    private final Class<?> type;

    /**
     * 构造函数：指定目标类型。
     *
     * @param type 目标 Java 类型
     */
    public MyJacksonTypeHandler(Class<?> type) {
        super(type);
        if (log.isTraceEnabled()) {
            log.trace("MyJacksonTypeHandler(" + type + ")");
        }
        Objects.requireNonNull(type, "Type argument cannot be null");
        this.type = type;
    }

    /**
     * JSON 反序列化：将 JSON 字符串解析为目标类型对象。
     *
     * @param json JSON 字符串
     * @return 目标类型对象
     */
    @Override
    public Object parse(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * JSON 序列化：将 Java 对象转换为 JSON 字符串。
     *
     * @param obj Java 对象
     * @return JSON 字符串
     */
    @Override
    public String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
