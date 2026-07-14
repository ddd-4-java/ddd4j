package io.ddd4j.sample.order.domain;

import io.ddd4j.core.ddd.model.ValueObject;
import io.ddd4j.kit.lang.StrKit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

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

    public static Money cny(BigDecimal amount) {
        return new Money(amount, "CNY");
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!Objects.equals(currency, other.currency())) {
            throw new IllegalArgumentException("currency must be same");
        }
        return new Money(amount.add(other.amount()), currency);
    }

    public Money multiply(int factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("factor must not be negative");
        }
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }
}
