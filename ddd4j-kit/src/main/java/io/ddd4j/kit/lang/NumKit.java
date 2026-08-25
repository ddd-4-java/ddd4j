/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    public final BigDecimal ZERO = new BigDecimal("0.00");

    // 是否是正数
    public boolean isPositive(BigDecimal value) {
        return Objects.nonNull(value) && value.signum() > 0;
    }


    // 是否是负数
    public boolean isNegative(BigDecimal value) {
        return Objects.nonNull(value) && value.signum() < 0;
    }


    // 是否为0
    public boolean isZero(BigDecimal value) {
        return Objects.nonNull(value) && value.signum() == 0;
    }


    // 是否为 0 或 null
    public boolean isZeroOrNull(BigDecimal value) {
        return Objects.isNull(value) || isZero(value);
    }


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
     * 四舍五入（保留两位小数）
     *
     * @return 新值（如果 value 为空返回 BigDecimal.ZERO）
     */
    public BigDecimal round2(BigDecimal value) {
        return round(value, 2);
    }


    // 四舍五入（保留两位小数）
    public String round2Str(BigDecimal value) {
        return round2(value).toString();
    }


    // 绝对值
    public BigDecimal abs(BigDecimal value) {
        return Objects.isNull(value) ? BigDecimal.ZERO : value.abs();
    }


    // 负绝对值
    public BigDecimal negativeAbs(BigDecimal value) {
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
    public BigDecimal add(BigDecimal value1, BigDecimal value2) {
        value1 = ObjKit.defaultIfNull(value1, BigDecimal.ZERO);
        value2 = ObjKit.defaultIfNull(value2, BigDecimal.ZERO);
        return value1.add(value2);
    }

    // 减法
    public BigDecimal sub(BigDecimal value1, BigDecimal value2) {
        value1 = ObjKit.defaultIfNull(value1, BigDecimal.ZERO);
        value2 = ObjKit.defaultIfNull(value2, BigDecimal.ZERO);
        return value1.subtract(value2);
    }


    // 加法（保留2位小数）
    public BigDecimal addRound2(BigDecimal bigNum1, BigDecimal bigNum2) {
        return round2(add(bigNum1, bigNum2));
    }


    // 减法（保留2位小数）
    public BigDecimal subRound2(BigDecimal bigNum1, BigDecimal bigNum2) {
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
