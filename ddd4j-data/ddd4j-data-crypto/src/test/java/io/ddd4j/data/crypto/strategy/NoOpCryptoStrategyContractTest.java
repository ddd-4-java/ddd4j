package io.ddd4j.data.crypto.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.data.crypto.enums.CryptoType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** \u9a8c\u8bc1 <code>NoOpCryptoStrategy</code> \u5728<code>roundTripsJson</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class NoOpCryptoStrategyContractTest {

    /** \u4f9b\u5f53\u524d\u7528\u4f8b\u6bd4\u8f83\u6216\u66ff\u6362\u7684\u7f16\u89e3\u7801\u3001\u8ba4\u8bc1\u6216\u7b5b\u9009\u7b56\u7565\u3002 */
    private final NoOpCryptoStrategy strategy = new NoOpCryptoStrategy(new ObjectMapper());

    /** \u9a8c\u8bc1<code>strategy.getType()</code> \u7b49\u4e8e <code>CryptoType.NOOP</code>\uff0c<code>serialized</code> \u7b49\u4e8e <code>"\"hello\""</code>\uff0c<code>strategy.decrypt(serialized, null, null, null, null, null, false, String.class)</code> \u7b49\u4e8e <code>"hello"</code>\u3002 */
    @Test
    void roundTripsJsonWithoutEncryption() {
        assertEquals(CryptoType.NOOP, strategy.getType());

        String serialized = strategy.encrypt("hello", null, null, null, null, null, false);

        assertEquals("\"hello\"", serialized);
        assertEquals("hello", strategy.decrypt(serialized, null, null, null, null, null, false, String.class));
    }

    /** \u9a8c\u8bc1<code>strategy.decrypt("{", null, null, null, null, null, false, String.class)</code> \u629b\u51fa <code>BizRuntimeException.class</code>\uff0c<code>strategy.hmac("hello", null, null, null, false)</code> \u7b49\u4e8e <code>""</code>\u3002 */
    @Test
    void reportsInvalidJsonAndKeepsHmacEmpty() {
        assertThrows(BizRuntimeException.class,
                () -> strategy.decrypt("{", null, null, null, null, null, false, String.class));
        assertEquals("", strategy.hmac("hello", null, null, null, false));
    }
}
