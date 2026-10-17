package io.ddd4j.sample.javalin.satoken.order.domain.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 金额值对象的 JSON 往返契约。 */
class MoneyJsonContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void normalizedMoneyRoundTripsAsAmountAndCurrency() throws Exception {
        Money original = new Money(new BigDecimal("12.345"), " cny ");
        String json = mapper.writeValueAsString(original);

        assertEquals(new BigDecimal("12.35"), mapper.readTree(json).get("amount").decimalValue());
        assertEquals("CNY", mapper.readTree(json).get("currency").asText());
        assertEquals(original, mapper.readValue(json, Money.class));
    }
}
