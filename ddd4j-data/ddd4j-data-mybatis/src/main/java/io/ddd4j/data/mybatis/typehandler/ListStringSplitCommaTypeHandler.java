package io.ddd4j.data.mybatis.typehandler;

import cn.hutool.core.util.StrUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 逗号风格返回list
 *
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class ListStringSplitCommaTypeHandler extends BaseTypeHandler<List<String>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, StrUtil.join(",", parameter));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String string = rs.getString(columnName);
        if (StrUtil.isBlank(string)) {
            return new ArrayList<>();
        }
        return Arrays.asList(string.split(","));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String string = rs.getString(columnIndex);
        if (StrUtil.isBlank(string)) {
            return new ArrayList<>();
        }
        return Arrays.asList(string.split(","));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String string = cs.getString(columnIndex);
        if (StrUtil.isBlank(string)) {
            return new ArrayList<>();
        }
        return Arrays.asList(string.split(","));
    }
}