package io.ddd4j.data.crypto.handler;

import io.ddd4j.data.crypto.strategy.CryptoStrategy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 <code>Ddd4jFieldCryptoHandler</code> 在<code>handlesNullNonStringAndEmptyValues</code>、<code>encryptsDecryptsAndPreservesInput</code>等操作中的行为契约。
 */
class Ddd4jFieldCryptoHandlerContractTest {

    /**
     * 验证<code>handler.getCryptoStrategy()</code> 与…为同一实例 <code>strategy</code>，<code>handler.encrypt(null)</code>为空，<code>calls.get()</code> 等于 <code>0</code>。
     */
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

    /**
     * 验证<code>handler.encrypt("hello")</code> 等于 <code>"cipher:hello"</code>，<code>handler.decrypt("cipher:hello")</code> 等于 <code>"hello"</code>，<code>calls.get()</code> 等于 <code>3</code>。
     */
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

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void requiresStrategy() {
        assertThrows(NullPointerException.class, () -> new Ddd4jFieldCryptoHandler(null));
    }

    /**
     * 按 <code>("encrypt".equals(method.getName()))</code> 等实际条件选择当前用例返回的结果。
     *
     * @param calls 传给 <code>Proxy.newProxyInstance()</code> 的 <code>AtomicInteger</code> 输入
     * @return 根据测试条件返回的可能值，包括 <code>"cipher:" + args[0]</code>、<code>value.substring("cipher:".length())</code>
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
