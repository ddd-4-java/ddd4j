package io.ddd4j.data.crypto.handler;

import io.ddd4j.data.crypto.strategy.CryptoStrategy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** \u9a8c\u8bc1 <code>Ddd4jFieldCryptoHandler</code> \u5728<code>handlesNullNonStringAndEmptyValues</code>\u3001<code>encryptsDecryptsAndPreservesInput</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class Ddd4jFieldCryptoHandlerContractTest {

    /** \u9a8c\u8bc1<code>handler.getCryptoStrategy()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>strategy</code>\uff0c<code>handler.encrypt(null)</code>\u4e3a\u7a7a\uff0c<code>calls.get()</code> \u7b49\u4e8e <code>0</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>handler.encrypt("hello")</code> \u7b49\u4e8e <code>"cipher:hello"</code>\uff0c<code>handler.decrypt("cipher:hello")</code> \u7b49\u4e8e <code>"hello"</code>\uff0c<code>calls.get()</code> \u7b49\u4e8e <code>3</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void requiresStrategy() {
        assertThrows(NullPointerException.class, () -> new Ddd4jFieldCryptoHandler(null));
    }

    /**
     * \u6309 <code>("encrypt".equals(method.getName()))</code> \u7b49\u5b9e\u9645\u6761\u4ef6\u9009\u62e9\u5f53\u524d\u7528\u4f8b\u8fd4\u56de\u7684\u7ed3\u679c\u3002
     * @param calls \u4f20\u7ed9 <code>Proxy.newProxyInstance()</code> \u7684 <code>AtomicInteger</code> \u8f93\u5165
     * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>"cipher:" + args[0]</code>\u3001<code>value.substring("cipher:".length())</code>
     */
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
