package io.ddd4j.data.mybatis.typehandler;

import cn.hutool.core.date.DateUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.extension.jackson.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

/**
 * Jackson JSON 类型处理器（零 MyBatis-Plus 依赖）。
 *
 * <p>基于 {@link ObjectMapper} 实现 JSON 字符串与 Java 对象的互转，
 * 使用自定义配置（时区 GMT+8、日期格式 yyyy-MM-dd HH:mm:ss、忽略 null 值）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@MappedTypes({List.class})
@MappedJdbcTypes(JdbcType.VARCHAR)
public class MyJacksonTypeHandler extends BaseTypeHandler<Object> {

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();
        OBJECT_MAPPER.setDefaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL));
        OBJECT_MAPPER.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        OBJECT_MAPPER.setDateFormat(DateUtil.newSimpleFormat("yyyy-MM-dd HH:mm:ss"));
        try {
            OBJECT_MAPPER.registerModule(new JavaTimeModule());
        } catch (Exception ignored) {
        }
    }

    private final Class<?> type;

    public MyJacksonTypeHandler(Class<?> type) {
        Objects.requireNonNull(type, "Type argument cannot be null");
        this.type = type;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, toJson(parameter));
    }

    @Override
    public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String str = rs.getString(columnName);
        return (str == null || str.isEmpty()) ? null : parse(str);
    }

    @Override
    public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String str = rs.getString(columnIndex);
        return (str == null || str.isEmpty()) ? null : parse(str);
    }

    @Override
    public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String str = cs.getString(columnIndex);
        return (str == null || str.isEmpty()) ? null : parse(str);
    }

    private Object parse(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
