package io.ddd4j.data.typehandlers;

import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * StrippedBigDecimalTypeHandler
 * <p>
 * 清理掉 BigDecimal 末尾多余的 0
 *
 * @author Jensen
 * @version 1.0
 * @公众号 架构师修行录
 * @date 2022/03/21 13:42
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
     * 清除末尾多余的0（如: 1.010 -> 1.01）
     *
     * @param value 数字
     * @return 当 value 为 null 时默认返回 0
     */
    public static BigDecimal clearZero(@Nullable BigDecimal value) {
        if (Objects.isNull(value)) {
            return BigDecimal.ZERO;
        }
        if (value.scale() == 0) {
            return value;
        }
        return new BigDecimal(value.stripTrailingZeros().toPlainString());
    }


}
