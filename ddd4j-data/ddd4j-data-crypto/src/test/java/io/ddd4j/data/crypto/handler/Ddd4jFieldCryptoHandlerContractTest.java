package io.ddd4j.data.crypto.handler;

import io.ddd4j.data.crypto.strategy.CryptoStrategy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Ddd4jFieldCryptoHandlerContractTest {

    @Test
    void handlesNullNonStringAndEmptyValuesWithoutCallingStrategy() {
        AtomicInteger calls = new AtomicInteger();
        CryptoStrategy strategy = strategy(calls);
        Ddd4jFieldCryptoHandler handler = new Ddd4jFieldCryptoHandler(strategy);
        Integer number = Integer.valueOf(42);
        String empty = "";

        assertSame(strategy, handler.getCryptoStrategy());
        assertNull(handler.encrypt(null));
        assertNull(handler.decrypt(null));
        assertSame(number, handler.decrypt(number));
        assertSame(empty, handler.decrypt(empty));
        assertEquals(0, calls.get());
    }

    @Test
    void encryptsDecryptsAndPreservesInputWhenDecryptFails() {
        AtomicInteger calls = new AtomicInteger();
        Ddd4jFieldCryptoHandler handler = new Ddd4jFieldCryptoHandler(strategy(calls));
        String broken = "broken";

        assertEquals("cipher:hello", handler.encrypt("hello"));
        assertEquals("hello", handler.decrypt("cipher:hello"));
        assertSame(broken, handler.decrypt(broken));
        assertEquals(3, calls.get());
    }

    @Test
    void requiresStrategy() {
        assertThrows(NullPointerException.class, () -> new Ddd4jFieldCryptoHandler(null));
    }

    private CryptoStrategy strategy(AtomicInteger calls) {
        return (CryptoStrategy) Proxy.newProxyInstance(
                CryptoStrategy.class.getClassLoader(),
                new Class<?>[]{CryptoStrategy.class},
                (proxy, method, args) -> {
                    calls.incrementAndGet();
                    if ("encrypt".equals(method.getName())) {
                        return "cipher:" + args[0];
                    }
                    if ("decrypt".equals(method.getName())) {
                        String value = (String) args[0];
                        if (!value.startsWith("cipher:")) {
                            throw new IllegalArgumentException("invalid ciphertext");
                        }
                        return value.substring("cipher:".length());
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
