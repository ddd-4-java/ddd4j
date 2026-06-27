/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.data.mybatis.handler;

import com.alibaba.fastjson2.JSONObject;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Deprecated(since = "3.4.x", forRemoval = true)
public class JSONObjectTypeHandler extends BaseTypeHandler<JSONObject> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JSONObject parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.toJSONString());
    }

    @Override
    public JSONObject getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String rtString = rs.getString(columnName);
        if ((rtString != null && !rtString.trim().isEmpty())) {
            return JSONObject.parseObject(rtString);
        }
        return null;
    }

    @Override
    public JSONObject getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String rtString = rs.getString(columnIndex);
        if ((rtString != null && !rtString.trim().isEmpty())) {
            return JSONObject.parseObject(rtString);
        }
        return null;
    }

    @Override
    public JSONObject getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String rtString = cs.getString(columnIndex);
        if ((rtString != null && !rtString.trim().isEmpty())) {
            return JSONObject.parseObject(rtString);
        }
        return null;
    }

}
