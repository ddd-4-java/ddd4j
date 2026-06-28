package io.ddd4j.data.mybatis.typehandler;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * 清理 {@link BigDecimal} 末尾多余的 0（如 {@code 1.010 -> 1.01}）。
 *
 * <p>继承 MyBatis 原生 {@link org.apache.ibatis.type.BigDecimalTypeHandler}，
 * 仅在读库时调用 {@link #clearZero(BigDecimal)} 去零。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class BigDecimalTypeHandler extends org.apache.ibatis.type.BigDecimalTypeHandler {

    @Override
    public BigDecimal getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return clearZero(super.getNullableResult(rs, columnIndex));
    }

    @Override
    public BigDecimal getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return clearZero(super.getNullableResult(rs, columnName));
    }

    @Override
    public BigDecimal getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return clearZero(super.getNullableResult(cs, columnIndex));
    }

    /**
     * 清除末尾多余的 0（如: {@code 1.010 -> 1.01}）。
     *
     * @param value 数字
     * @return 当 value 为 null 时默认返回 0
     */
    public static BigDecimal clearZero(BigDecimal value) {
        if (Objects.isNull(value)) {
            return BigDecimal.ZERO;
        }
        if (value.scale() == 0) {
            return value;
        }
        return new BigDecimal(value.stripTrailingZeros().toPlainString());
    }

}
