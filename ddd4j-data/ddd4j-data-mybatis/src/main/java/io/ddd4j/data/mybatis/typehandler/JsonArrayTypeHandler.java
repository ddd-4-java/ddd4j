package io.ddd4j.data.mybatis.typehandler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
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
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@MappedTypes({JSONArray.class})
@MappedJdbcTypes({JdbcType.VARCHAR})
public class JsonArrayTypeHandler extends BaseTypeHandler<JSONArray> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JSONArray parameter,
                                    JdbcType jdbcType) throws SQLException {
        ps.setString(i, JSONUtil.toJsonStr(parameter));
    }

    @Override
    public JSONArray getNullableResult(ResultSet rs, String columnName)
            throws SQLException {
        if (StrUtil.isNotBlank(rs.getString(columnName))) {
            return JSONUtil.parseArray(rs.getString(columnName));
        } else {
            return null;
        }
    }

    @Override
    public JSONArray getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        if (StrUtil.isNotBlank(rs.getString(columnIndex))) {
            return JSONUtil.parseArray(rs.getString(columnIndex));
        } else {
            return null;
        }
    }

    @Override
    public JSONArray getNullableResult(CallableStatement cs, int columnIndex)
            throws SQLException {
        if (StrUtil.isNotBlank(cs.getString(columnIndex))) {
            return JSONUtil.parseArray(cs.getString(columnIndex));
        } else {
            return null;
        }
    }

}
