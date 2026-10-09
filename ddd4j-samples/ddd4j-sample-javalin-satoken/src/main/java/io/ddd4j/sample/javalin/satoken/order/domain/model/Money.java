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
package io.ddd4j.sample.javalin.satoken.order.domain.model;

import io.ddd4j.core.ddd.model.ValueObject;
import io.ddd4j.kit.lang.StrKit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * 金额值对象。
 *
 * <p>封装不可变的金额与货币代码，提供加减乘运算与折扣计算。
 * 构造期校验非负与货币一致性，是订单聚合内常用值对象。
 *
 * <p>使用 {@code record} 实现 {@link ValueObject} 接口，
 * 自动获得不可变性、equals/hashCode/toString。
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
     */
    public static Money cny(String amount) {
        return new Money(new BigDecimal(Objects.requireNonNull(amount, "amount must not be null")), "CNY");
    }

    /**
     * 创建指定货币的零金额。
     */
    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /**
     * 金额相加（币种必须一致）。
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
     */
    public Money multiply(int factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("factor must not be negative");
        }
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    /**
     * 应用折扣（百分比）。
     */
    public Money discount(int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("percent must be in [0,100]");
        }
        BigDecimal factor = BigDecimal.valueOf(100 - percent)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return new Money(amount.multiply(factor), currency);
    }
}