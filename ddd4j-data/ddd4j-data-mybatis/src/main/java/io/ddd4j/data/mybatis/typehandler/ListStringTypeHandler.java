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
import java.util.List;

/**
 * 使用该对象需要在xml中指定 typeHandler
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@MappedTypes(List.class)
@MappedJdbcTypes({JdbcType.VARCHAR})
public class ListStringTypeHandler implements TypeHandler<List<String>> {

    @Override
    public void setParameter(PreparedStatement ps, int i, List<String> strings, JdbcType jdbcType) throws SQLException {
        ps.setString(i, JSONUtil.toJsonStr(strings));
    }

    @Override
    public List<String> getResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return toList(value);
    }

    @Override
    public List<String> getResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return toList(value);
    }

    @Override
    public List<String> getResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return toList(value);
    }

    private List<String> toList(String value) {
        if (value == null) {
            return null;
        }
        return JSONUtil.parseArray(value).toList(String.class);
    }
}