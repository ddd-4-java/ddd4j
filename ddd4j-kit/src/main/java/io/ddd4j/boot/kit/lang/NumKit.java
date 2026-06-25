package io.ddd4j.boot.kit.lang;

import cn.hutool.core.util.NumberUtil;
import lombok.experimental.UtilityClass;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

/**
 * 数字工具类
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
@UtilityClass
public class NumKit extends NumberUtil {

    public final BigDecimal ZERO = new BigDecimal("0.00");

    // 是否是正数
    public boolean isPositive(@Nullable BigDecimal value) {
        return Objects.nonNull(value) && value.signum() > 0;
    }


    // 是否是负数
    public boolean isNegative(@Nullable BigDecimal value) {
        return Objects.nonNull(value) && value.signum() < 0;
    }


    // 是否为0
    public boolean isZero(@Nullable BigDecimal value) {
        return Objects.nonNull(value) && value.signum() == 0;
    }


    // 是否为 0 或 null
    public boolean isZeroOrNull(@Nullable BigDecimal value) {
        return Objects.isNull(value) || isZero(value);
    }


    public boolean isGreaterThanZero(@NonNull BigDecimal value) {
        return isGreaterOne(value, ZERO) > 0;
    }

    /**
     * v1 大于等于 v2
     *
     * @param v1 v1
     * @param v2 v2
     * @return 大于等于true 否则false
     */
    public boolean isGreaterOrEquals(@NonNull BigDecimal v1, @NonNull BigDecimal v2) {
        return isGreaterOne(v1, v2) >= 0;
    }

    /**
     * 比较两个BigDecimal大小
     *
     * @param v1 BigDecimal1
     * @param v2 BigDecimal2
     * @return v1 > v2 return 1  v1 = v2 return 0 v1 < v2 return -1
     */
    public int isGreaterOne(@NonNull BigDecimal v1, @NonNull BigDecimal v2) {
        return v1.compareTo(v2);
    }

    /**
     * 四舍五入（保留两位小数）
     *
     * @return 新值（如果 value 为空返回 BigDecimal.ZERO）
     */
    public BigDecimal round2(@Nullable BigDecimal value) {
        return round(value, 2);
    }


    // 四舍五入（保留两位小数）
    public String round2Str(@Nullable BigDecimal value) {
        return round2(value).toString();
    }


    // 绝对值
    public BigDecimal abs(@Nullable BigDecimal value) {
        return Objects.isNull(value) ? BigDecimal.ZERO : value.abs();
    }


    // 负绝对值
    public BigDecimal negativeAbs(@Nullable BigDecimal value) {
        return Objects.isNull(value) ? BigDecimal.ZERO : value.abs().negate();
    }

    // 求和
    public BigDecimal sum(Collection<BigDecimal> values) {
        if (CollKit.isEmpty(values)) {
            return BigDecimal.ZERO;
        }
        return add(values.toArray(new BigDecimal[0]));
    }

    /**
     * 清除末尾多余的0（如: 1.010 -> 1.01）
     *
     * @param value 数字
     * @return 当 value 为 null 时默认返回 0
     */
    public BigDecimal clearZero(@Nullable BigDecimal value) {
        if (Objects.isNull(value)) {
            return BigDecimal.ZERO;
        }
        if (value.scale() == 0) {
            return value;
        }
        return new BigDecimal(value.stripTrailingZeros().toPlainString());
    }


    // 清除末尾多余的0（如: 1.010 -> 1.01）
    public String clearZero(String value) {
        return new BigDecimal(value).stripTrailingZeros().toPlainString();
    }


    /**
     * 比较大小，值相等 返回true<br>
     * 此方法通过调用{@link BigDecimal#compareTo(BigDecimal)}方法来判断是否相等<br>
     * 此方法判断值相等时忽略精度的，即0.00 == 0
     *
     * @param bigNum1 数字1
     * @param bigNum2 数字2
     * @return 是否相等
     */
    public boolean equals(BigDecimal bigNum1, String bigNum2) {
        return equals(bigNum1, new BigDecimal(bigNum2));
    }

    /**
     * v1 大于 v2
     *
     * @param v1 v1
     * @param v2 v2
     * @return 大于 true 否则false
     */
    public boolean isGreaterOther(@NonNull BigDecimal v1, @NonNull BigDecimal v2) {
        return isGreaterOne(v1, v2) == 1;
    }

    /**
     * v1 == v2
     *
     * @param v1 v1
     * @param v2 v2
     * @return 等于 true 否则false
     */
    public boolean isBothAreEqual(@NonNull BigDecimal v1, @NonNull BigDecimal v2) {
        return isGreaterOne(v1, v2) == 0;
    }

    /**
     * v1 < v2
     *
     * @param v1 v1
     * @param v2 v2
     * @return 小于 true 否则false
     */
    public boolean isLessThanOther(@NonNull BigDecimal v1, @NonNull BigDecimal v2) {
        return isGreaterOne(v1, v2) == -1;
    }

    // 求和
    public BigDecimal add(@Nullable BigDecimal value1, @Nullable BigDecimal value2) {
        value1 = ObjKit.defaultIfNull(value1, BigDecimal.ZERO);
        value2 = ObjKit.defaultIfNull(value2, BigDecimal.ZERO);
        return value1.add(value2);
    }

    // 减法
    public BigDecimal sub(@Nullable BigDecimal value1, @Nullable BigDecimal value2) {
        value1 = ObjKit.defaultIfNull(value1, BigDecimal.ZERO);
        value2 = ObjKit.defaultIfNull(value2, BigDecimal.ZERO);
        return value1.subtract(value2);
    }


    // 加法（保留2位小数）
    public BigDecimal addRound2(@Nullable BigDecimal bigNum1, @Nullable BigDecimal bigNum2) {
        return round2(add(bigNum1, bigNum2));
    }


    // 减法（保留2位小数）
    public BigDecimal subRound2(@Nullable BigDecimal bigNum1, @Nullable BigDecimal bigNum2) {
        return round2(sub(bigNum1, bigNum2));
    }


    // 除法（保留2位小数4舍5入）
    public BigDecimal divRound2(String v1, String v2) {
        return div(v1, v2, 2, RoundingMode.HALF_UP);
    }


    // 除法（保留2位小数4舍5入）
    public BigDecimal divRound2(Integer v1, Integer v2) {
        return divRound2(v1.toString(), v2.toString());
    }


}
