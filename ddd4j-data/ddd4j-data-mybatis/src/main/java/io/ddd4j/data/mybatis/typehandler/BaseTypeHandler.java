package io.ddd4j.data.mybatis.typehandler;

import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeReference;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * 自定义类型处理器基类：varchar &lt;-&gt; T。
 *
 * <p>子类只需实现 {@link #convert(Object)}（写库）和 {@link #parse(String)}（读库），
 * 通过反射自动推断泛型类型。
 *
 * <p>从旧 ddd4j {@code base-data/typehandlers/BaseTypeHandler} 迁移，
 * 解决 MyBatis 不支持 List/JSON/数组等复杂类型列存储的痛点。
 *
 * @param <T> 目标 Java 类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@SuppressWarnings("unchecked")
public abstract class BaseTypeHandler<T> extends org.apache.ibatis.type.BaseTypeHandler<T> {

    /**
     * 获取实际的泛型类型（通过反射推断）。
     *
     * @return 泛型类型
     */
    public Class<T> type() {
        return (Class<T>) ReflectionKit.getSuperClassGenericType(this.getClass(), BaseTypeHandler.class, 0);
    }

    /**
     * 获取实际的引用类型。
     *
     * @return TypeReference
     */
    public TypeReference<T> typeReference() {
        return new TypeReference<T>() {
        };
    }

    /**
     * 把指定类型转换为字符串类型，对应写库。
     *
     * @param obj 待转换对象
     * @return 字符串
     */
    protected abstract String convert(T obj);

    /**
     * 把字符串类型解析成指定类型，对应读库。
     *
     * @param result 数据库读取的字符串
     * @return 解析后的对象
     */
    protected abstract T parse(String result);

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
        if (Objects.isNull(parameter)) {
            return;
        }
        ps.setString(i, this.convert(parameter));
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String str = rs.getString(columnName);
        return Objects.isNull(str) || !org.springframework.util.StringUtils.hasLength(str) ? null : this.parse(str);
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String str = rs.getString(columnIndex);
        return Objects.isNull(str) || !org.springframework.util.StringUtils.hasLength(str) ? null : this.parse(str);
    }

    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String str = cs.getString(columnIndex);
        return Objects.isNull(str) || !org.springframework.util.StringUtils.hasLength(str) ? null : this.parse(str);
    }

}
