package io.ddd4j.data.mybatis.typehandler;

import cn.hutool.json.JSONUtil;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 使用该对象需要在xml中指定 typeHandler
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@MappedTypes(Set.class)
@MappedJdbcTypes({JdbcType.VARCHAR})
public class SetStringTypeHandler implements TypeHandler<Set<String>> {

    @Override
    public void setParameter(PreparedStatement ps, int i, Set<String> strings, JdbcType jdbcType) throws SQLException {
        ps.setString(i, JSONUtil.toJsonStr(strings));
    }

    @Override
    public Set<String> getResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return toList(value);
    }

    @Override
    public Set<String> getResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return toList(value);
    }

    @Override
    public Set<String> getResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return toList(value);
    }

    private Set<String> toList(String value) {
        if (Objects.isNull(value)) {
            return null;
        }
        return new HashSet<>(JSONUtil.parseArray(value).toList(String.class));
    }
}