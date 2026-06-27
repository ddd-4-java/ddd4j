/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.core.mybatis.handler;

import com.alibaba.fastjson2.JSONArray;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Deprecated(since = "3.4.x", forRemoval = true)
public class JSONArrayTypeHandler extends BaseTypeHandler<JSONArray> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JSONArray parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.toString());
    }

    @Override
    public JSONArray getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String rtString = rs.getString(columnName);
        if ((rtString != null && !rtString.trim().isEmpty())) {
            return JSONArray.parseArray(rtString);
        }
        return null;
    }

    @Override
    public JSONArray getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String rtString = rs.getString(columnIndex);
        if ((rtString != null && !rtString.trim().isEmpty())) {
            return JSONArray.parseArray(rtString);
        }
        return null;
    }

    @Override
    public JSONArray getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String rtString = cs.getString(columnIndex);
        if ((rtString != null && !rtString.trim().isEmpty())) {
            return JSONArray.parseArray(rtString);
        }
        return null;
    }

}
