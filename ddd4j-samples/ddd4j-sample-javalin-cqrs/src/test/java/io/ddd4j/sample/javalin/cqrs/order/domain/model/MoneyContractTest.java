/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.sample.javalin.cqrs.order.domain.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyContractTest {

    @Test
    void amount_shouldNormalizeAndPreserveValueAndJsonContract() throws Exception {
        Money money = new Money(new BigDecimal("1.235"), " cny ");
        Money equal = new Money(new BigDecimal("1.24"), "CNY");
        assertEquals(new BigDecimal("1.24"), money.amount());
        assertEquals("CNY", money.currency());
        assertEquals(money.amount(), money.getAmount());
        assertEquals(money.currency(), money.getCurrency());
        assertEquals(equal, money);
        assertEquals(equal.hashCode(), money.hashCode());
        assertEquals(297289, money.hashCode());
        assertNotEquals(money, Money.cny("2"));
        assertEquals("Money[amount=1.24, currency=CNY]", money.toString());
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(money, mapper.readValue(mapper.writeValueAsString(money), Money.class));
        assertEquals(new BigDecimal("2.48"), money.multiply(2).amount());
        assertThrows(IllegalArgumentException.class, () -> money.add(new Money(BigDecimal.ONE, "USD")));
    }

    @Test
    void javaSerialization_shouldRestoreValidatedValue() throws Exception {
        Money money = Money.cny("3.50");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(money);
        }
        Money restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (Money) in.readObject();
        }
        assertEquals(money, restored);
        assertEquals(money.toString(), restored.toString());
    }
}
