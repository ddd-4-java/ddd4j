package io.ddd4j.data.crypto.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.data.crypto.enums.CryptoType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 <code>NoOpCryptoStrategy</code> 在<code>roundTripsJson</code>等操作中的行为契约。
 */
class NoOpCryptoStrategyContractTest {

    /**
     * 供当前用例比较或替换的编解码、认证或筛选策略。
     */
    private final NoOpCryptoStrategy strategy = new NoOpCryptoStrategy(new ObjectMapper());

    /**
     * 验证<code>strategy.getType()</code> 等于 <code>CryptoType.NOOP</code>，<code>serialized</code> 等于 <code>"\"hello\""</code>，<code>strategy.decrypt(serialized, null, null, null, null, null, false, String.class)</code> 等于 <code>"hello"</code>。
     */
    @Test
    void roundTripsJsonWithoutEncryption() {
        assertEquals(CryptoType.NOOP, strategy.getType());

        String serialized = strategy.encrypt("hello", null, null, null, null, null, false);

        assertEquals("\"hello\"", serialized);
        assertEquals("hello", strategy.decrypt(serialized, null, null, null, null, null, false, String.class));
    }

    /**
     * 验证<code>strategy.decrypt("{", null, null, null, null, null, false, String.class)</code> 抛出 <code>BizRuntimeException.class</code>，<code>strategy.hmac("hello", null, null, null, false)</code> 等于 <code>""</code>。
     */
    @Test
    void reportsInvalidJsonAndKeepsHmacEmpty() {
        assertThrows(BizRuntimeException.class,
                () -> strategy.decrypt("{", null, null, null, null, null, false, String.class));
        assertEquals("", strategy.hmac("hello", null, null, null, false));
    }
}
