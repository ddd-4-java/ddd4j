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
package io.ddd4j.sample.order.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.io.InvalidObjectException;

import java.io.ObjectStreamException;

import io.ddd4j.core.ddd.model.ValueObject;
import io.ddd4j.kit.lang.StrKit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/** 金额与币种组成的不可变值对象。 */
public final class Money implements ValueObject {

    private static final long serialVersionUID = 0L;

    /** 保留两位小数的非负金额。 */
    private final BigDecimal amount;

    /** 标准化的大写币种。 */
    private final String currency;

    /**
     * 创建金额值对象。
     *
     * @param amount 非负金额
     * @param currency 币种
     */
    @JsonCreator()
    public Money(@JsonProperty("amount") BigDecimal amount, @JsonProperty("currency") String currency) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (StrKit.isBlank(currency)) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        currency = currency.trim().toUpperCase(Locale.ROOT);
        this.amount = amount;
        this.currency = currency;
    }

    /**
     * @param amount 人民币金额
     * @return 人民币金额对象
     */
    public static Money cny(BigDecimal amount) {
        return new Money(amount, "CNY");
    }

    /**
     * @param currency 币种
     * @return 该币种的零金额
     */
    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /**
     * @param other 同币种的另一金额
     * @return 相加后的金额
     */
    public Money add(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!Objects.equals(currency, other.currency())) {
            throw new IllegalArgumentException("currency must be same");
        }
        return new Money(amount.add(other.amount()), currency);
    }

    /**
     * @param factor 非负乘数
     * @return 相乘后的金额
     */
    public Money multiply(int factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("factor must not be negative");
        }
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    /** @return 金额 */
    @JsonProperty("amount")
    public BigDecimal amount() {
        return amount;
    }

    /** @return 币种 */
    @JsonProperty("currency")
    public String currency() {
        return currency;
    }

    /** @return 金额 */
    public BigDecimal getAmount() {
        return amount;
    }

    /** @return 币种 */
    public String getCurrency() {
        return currency;
    }

    /**
     * 反序列化时重新校验金额约束。
     *
     * @return 校验后的金额对象
     * @throws ObjectStreamException 反序列化数据不合法时抛出
     */
    private Object readResolve() throws ObjectStreamException {
        try {
            return new Money(amount, currency);
        } catch (RuntimeException cause) {
            InvalidObjectException failure = new InvalidObjectException(cause.getMessage());
            failure.initCause(cause);
            throw failure;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        Money other = (Money) obj;
        return Objects.equals(this.amount, other.amount) && Objects.equals(this.currency, other.currency);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(amount);
        result = 31 * result + Objects.hashCode(currency);
        return result;
    }

    @Override
    public String toString() {
        return "Money(amount=" + amount + ", currency=" + currency + ")";
    }
}
