package io.ddd4j.boot.cmpt.mybatis.typehandler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 存储到数据库, 将JSON对象转换成字符串;
 * 从数据库获取数据, 将字符串转为JSON对象.
 *
 * 
 */
@MappedTypes({JSONArray.class})
@MappedJdbcTypes({JdbcType.VARCHAR})
public class FastJsonArrayTypeHandler extends BaseTypeHandler<JSONArray> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JSONArray parameter,
                                    JdbcType jdbcType) throws SQLException {
        ps.setString(i, JSON.toJSONString(parameter));
    }

    @Override
    public JSONArray getNullableResult(ResultSet rs, String columnName)
            throws SQLException {
        return JSON.parseArray(rs.getString(columnName));
    }

    @Override
    public JSONArray getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return JSON.parseArray(rs.getString(columnIndex));
    }

    @Override
    public JSONArray getNullableResult(CallableStatement cs, int columnIndex)
            throws SQLException {
        return JSON.parseArray(cs.getString(columnIndex));
    }

}
