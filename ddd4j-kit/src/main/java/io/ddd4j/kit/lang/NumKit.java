package io.ddd4j.kit.lang;

import cn.hutool.core.util.NumberUtil;
import lombok.experimental.UtilityClass;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

/**
 * 数字工具类
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@UtilityClass
public class NumKit extends NumberUtil {

    /** \u6570\u503c\u96f6\u5e38\u91cf\uff08\u4fdd\u7559\u4e24\u4f4d\u5c0f\u6570\uff09\u3002 */
    public final BigDecimal ZERO = new BigDecimal("0.00");

    // 是否是正数
    /**
     * \u5224\u65ad\u6570\u503c\u662f\u5426\u5927\u4e8e\u96f6\u3002
     * @param value \u5f85\u5224\u65ad\u6570\u503c
     * @return \u5927\u4e8e\u96f6\u65f6\u8fd4\u56de true
     */
    public boolean isPositive(BigDecimal value) {
        return Objects.nonNull(value) && value.signum() > 0;
    }


    // 是否是负数
    /**
     * \u5224\u65ad\u6570\u503c\u662f\u5426\u5c0f\u4e8e\u96f6\u3002
     * @param value \u5f85\u5224\u65ad\u6570\u503c
     * @return \u5c0f\u4e8e\u96f6\u65f6\u8fd4\u56de true
     */
    public boolean isNegative(BigDecimal value) {
        return Objects.nonNull(value) && value.signum() < 0;
    }


    // 是否为0
    /**
     * \u5224\u65ad\u6570\u503c\u662f\u5426\u7b49\u4e8e\u96f6\u3002
     * @param value \u5f85\u5224\u65ad\u6570\u503c
     * @return \u7b49\u4e8e\u96f6\u65f6\u8fd4\u56de true
     */
    public boolean isZero(BigDecimal value) {
        return Objects.nonNull(value) && value.signum() == 0;
    }


    // 是否为 0 或 null
    /**
     * \u5224\u65ad\u6570\u503c\u662f\u5426\u4e3a\u7a7a\u6216\u7b49\u4e8e\u96f6\u3002
     * @param value \u5f85\u5224\u65ad\u6570\u503c
     * @return \u4e3a\u7a7a\u6216\u7b49\u4e8e\u96f6\u65f6\u8fd4\u56de true
     */
    public boolean isZeroOrNull(BigDecimal value) {
        return Objects.isNull(value) || isZero(value);
    }


    /**
     * \u5224\u65ad\u6570\u503c\u662f\u5426\u5927\u4e8e\u96f6\u3002
     * @param value \u5f85\u5224\u65ad\u6570\u503c
     * @return \u5927\u4e8e\u96f6\u65f6\u8fd4\u56de true
     */
    public boolean isGreaterThanZero(BigDecimal value) {
        return isGreaterOne(value, ZERO) > 0;
    }

    /**
     * v1 大于等于 v2
     *
     * @param v1 v1
     * @param v2 v2
     * @return 大于等于true 否则false
     */
    public boolean isGreaterOrEquals(BigDecimal v1, BigDecimal v2) {
        return isGreaterOne(v1, v2) >= 0;
    }

    /**
     * 比较两个BigDecimal大小
     *
     * @param v1 BigDecimal1
     * @param v2 BigDecimal2
     * @return {@code 1} when v1 is greater than v2, {@code 0} when equal, otherwise {@code -1}
     */
    public int isGreaterOne(BigDecimal v1, BigDecimal v2) {
        return v1.compareTo(v2);
    }

    /**
     * \u56db\u820d\u4e94\u5165\uff08\u4fdd\u7559\u4e24\u4f4d\u5c0f\u6570\uff09
     * @return \u65b0\u503c\uff08\u5982\u679c value \u4e3a\u7a7a\u8fd4\u56de BigDecimal.ZERO\uff09
     *
     * @param value \u5f85\u56db\u820d\u4e94\u5165\u7684\u6570\u503c
     */
    public BigDecimal round2(BigDecimal value) {
        return round(value, 2);
    }


    // 四舍五入（保留两位小数）
    /**
     * \u5c06\u6570\u503c\u56db\u820d\u4e94\u5165\u5230\u4e24\u4f4d\u5c0f\u6570\u5e76\u8f6c\u4e3a\u5b57\u7b26\u4e32\u3002
     * @param value \u5f85\u5904\u7406\u6570\u503c
     * @return \u4e24\u4f4d\u5c0f\u6570\u7684\u5b57\u7b26\u4e32\u8868\u793a
     */
    public String round2Str(BigDecimal value) {
        return round2(value).toString();
    }


    // 绝对值
    /**
     * \u8fd4\u56de\u6570\u503c\u7edd\u5bf9\u503c\u3002
     * @param value \u5f85\u5904\u7406\u6570\u503c
     * @return \u7edd\u5bf9\u503c\uff1b\u8f93\u5165\u4e3a\u7a7a\u65f6\u8fd4\u56de\u96f6
     */
    public BigDecimal abs(BigDecimal value) {
        return Objects.isNull(value) ? BigDecimal.ZERO : value.abs();
    }


    // 负绝对值
    /**
     * \u8fd4\u56de\u6570\u503c\u7edd\u5bf9\u503c\u7684\u8d1f\u503c\u3002
     * @param value \u5f85\u5904\u7406\u6570\u503c
     * @return \u8d1f\u7edd\u5bf9\u503c\uff1b\u8f93\u5165\u4e3a\u7a7a\u65f6\u8fd4\u56de\u96f6
     */
    public BigDecimal negativeAbs(BigDecimal value) {
        return Objects.isNull(value) ? BigDecimal.ZERO : value.abs().negate();
    }

    // 求和
    /**
     * \u6c42\u96c6\u5408\u4e2d\u6570\u503c\u4e4b\u548c\u3002
     * @param values \u6570\u503c\u96c6\u5408
     * @return \u6c42\u548c\u7ed3\u679c\uff1b\u96c6\u5408\u4e3a\u7a7a\u65f6\u8fd4\u56de\u96f6
     */
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
    public BigDecimal clearZero(BigDecimal value) {
        if (Objects.isNull(value)) {
            return BigDecimal.ZERO;
        }
        if (value.scale() == 0) {
            return value;
        }
        return new BigDecimal(value.stripTrailingZeros().toPlainString());
    }


    // 清除末尾多余的0（如: 1.010 -> 1.01）
    /**
     * \u79fb\u9664\u6570\u5b57\u6587\u672c\u672b\u5c3e\u591a\u4f59\u7684\u96f6\u3002
     * @param value \u6570\u5b57\u6587\u672c
     * @return \u79fb\u9664\u591a\u4f59\u96f6\u540e\u7684\u6570\u5b57\u6587\u672c
     */
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
    public boolean isGreaterOther(BigDecimal v1, BigDecimal v2) {
        return isGreaterOne(v1, v2) == 1;
    }

    /**
     * v1 == v2
     *
     * @param v1 v1
     * @param v2 v2
     * @return 等于 true 否则false
     */
    public boolean isBothAreEqual(BigDecimal v1, BigDecimal v2) {
        return isGreaterOne(v1, v2) == 0;
    }

    /**
     * v1 &lt; v2
     *
     * @param v1 v1
     * @param v2 v2
     * @return 小于 true 否则false
     */
    public boolean isLessThanOther(BigDecimal v1, BigDecimal v2) {
        return isGreaterOne(v1, v2) == -1;
    }

    // 求和
    /**
     * \u5c06\u4e24\u4e2a\u6570\u503c\u76f8\u52a0\uff0c\u7a7a\u503c\u6309\u96f6\u5904\u7406\u3002
     * @param value1 \u7b2c\u4e00\u4e2a\u6570\u503c
     * @param value2 \u7b2c\u4e8c\u4e2a\u6570\u503c
     * @return \u76f8\u52a0\u7ed3\u679c
     */
    public BigDecimal add(BigDecimal value1, BigDecimal value2) {
        value1 = ObjKit.defaultIfNull(value1, BigDecimal.ZERO);
        value2 = ObjKit.defaultIfNull(value2, BigDecimal.ZERO);
        return value1.add(value2);
    }

    // 减法
    /**
     * \u5c06\u4e24\u4e2a\u6570\u503c\u76f8\u51cf\uff0c\u7a7a\u503c\u6309\u96f6\u5904\u7406\u3002
     * @param value1 \u88ab\u51cf\u6570
     * @param value2 \u51cf\u6570
     * @return \u76f8\u51cf\u7ed3\u679c
     */
    public BigDecimal sub(BigDecimal value1, BigDecimal value2) {
        value1 = ObjKit.defaultIfNull(value1, BigDecimal.ZERO);
        value2 = ObjKit.defaultIfNull(value2, BigDecimal.ZERO);
        return value1.subtract(value2);
    }


    // 加法（保留2位小数）
    /**
     * \u76f8\u52a0\u540e\u56db\u820d\u4e94\u5165\u5230\u4e24\u4f4d\u5c0f\u6570\u3002
     * @param bigNum1 \u7b2c\u4e00\u4e2a\u6570\u503c
     * @param bigNum2 \u7b2c\u4e8c\u4e2a\u6570\u503c
     * @return \u4fdd\u7559\u4e24\u4f4d\u5c0f\u6570\u7684\u548c
     */
    public BigDecimal addRound2(BigDecimal bigNum1, BigDecimal bigNum2) {
        return round2(add(bigNum1, bigNum2));
    }


    // 减法（保留2位小数）
    /**
     * \u76f8\u51cf\u540e\u56db\u820d\u4e94\u5165\u5230\u4e24\u4f4d\u5c0f\u6570\u3002
     * @param bigNum1 \u88ab\u51cf\u6570
     * @param bigNum2 \u51cf\u6570
     * @return \u4fdd\u7559\u4e24\u4f4d\u5c0f\u6570\u7684\u5dee
     */
    public BigDecimal subRound2(BigDecimal bigNum1, BigDecimal bigNum2) {
        return round2(sub(bigNum1, bigNum2));
    }


    // 除法（保留2位小数4舍5入）
    /**
     * \u76f8\u9664\u540e\u56db\u820d\u4e94\u5165\u5230\u4e24\u4f4d\u5c0f\u6570\u3002
     * @param v1 \u88ab\u9664\u6570
     * @param v2 \u9664\u6570
     * @return \u4fdd\u7559\u4e24\u4f4d\u5c0f\u6570\u7684\u5546
     */
    public BigDecimal divRound2(String v1, String v2) {
        return div(v1, v2, 2, RoundingMode.HALF_UP);
    }


    // 除法（保留2位小数4舍5入）
    /**
     * \u76f8\u9664\u540e\u56db\u820d\u4e94\u5165\u5230\u4e24\u4f4d\u5c0f\u6570\u3002
     * @param v1 \u88ab\u9664\u6570
     * @param v2 \u9664\u6570
     * @return \u4fdd\u7559\u4e24\u4f4d\u5c0f\u6570\u7684\u5546
     */
    public BigDecimal divRound2(Integer v1, Integer v2) {
        return divRound2(v1.toString(), v2.toString());
    }


}
