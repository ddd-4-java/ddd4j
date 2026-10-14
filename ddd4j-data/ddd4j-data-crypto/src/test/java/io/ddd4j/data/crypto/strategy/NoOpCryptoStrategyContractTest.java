package io.ddd4j.data.crypto.strategy;

import tools.jackson.databind.ObjectMapper;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.data.crypto.enums.CryptoType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoOpCryptoStrategyContractTest {

    private final NoOpCryptoStrategy strategy = new NoOpCryptoStrategy(new ObjectMapper());

    @Test
    void roundTripsJsonWithoutEncryption() {
        assertEquals(CryptoType.NOOP, strategy.getType());

        String serialized = strategy.encrypt("hello", null, null, null, null, null, false);

        assertEquals("\"hello\"", serialized);
        assertEquals("hello", strategy.decrypt(serialized, null, null, null, null, null, false, String.class));
    }

    @Test
    void reportsInvalidJsonAndKeepsHmacEmpty() {
        assertThrows(BizRuntimeException.class,
                () -> strategy.decrypt("{", null, null, null, null, null, false, String.class));
        assertEquals("", strategy.hmac("hello", null, null, null, false));
    }
}
