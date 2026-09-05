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
package io.ddd4j.sample.quarkus.cqrs.order.domain.model;

import io.ddd4j.core.ddd.model.ValueObject;
import io.ddd4j.kit.lang.StrKit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * 金额值对象（富模型示例使用）。
 *
 * <p>作为不可变值对象，参与金额比较、加减乘除、序列化等操作。
 *
 * @param amount   金额数值
 * @param currency 货币代码（如 CNY、USD）
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public record Money(BigDecimal amount, String currency) implements ValueObject {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (StrKit.isBlank(currency)) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        currency = currency.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 创建人民币金额。
     *
     * @param amount 金额字符串
     * @return Money 实例
     */
    public static Money cny(String amount) {
        return new Money(new BigDecimal(Objects.requireNonNull(amount, "amount must not be null")), "CNY");
    }

    /**
     * 创建指定货币的零金额。
     *
     * @param currency 货币代码
     * @return 零金额实例
     */
    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /**
     * 金额相加。
     *
     * @param other 另一个金额
     * @return 相加后的金额
     * @throws IllegalArgumentException 如果货币代码不一致
     */
    public Money add(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!Objects.equals(currency, other.currency())) {
            throw new IllegalArgumentException("currency must be same");
        }
        return new Money(amount.add(other.amount()), currency);
    }

    /**
     * 金额乘以整数因子。
     *
     * @param factor 倍数因子
     * @return 相乘后的金额
     * @throws IllegalArgumentException 如果因子为负数
     */
    public Money multiply(int factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("factor must not be negative");
        }
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }
}