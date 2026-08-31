package io.ddd4j.core.mybatis.handler;

import io.ddd4j.core.enums.BooleanEnum;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(BooleanEnum.class)
@MappedJdbcTypes(JdbcType.INTEGER)
@Deprecated
public class CustomBooleanEnumTypeHandler extends BaseTypeHandler<BooleanEnum> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, BooleanEnum parameter, JdbcType jdbcType) throws SQLException {
        ps.setInt(i, parameter.getValue());
    }

    @Override
    public BooleanEnum getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : BooleanEnum.valueOf(value);
    }

    @Override
    public BooleanEnum getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int value = rs.getInt(columnIndex);
        return rs.wasNull() ? null : BooleanEnum.valueOf(value);
    }

    @Override
    public BooleanEnum getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int value = cs.getInt(columnIndex);
        return cs.wasNull() ? null : BooleanEnum.valueOf(value);
    }
}
