package io.ddd4j.mq.serialization;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 通过 <code>serialization.serialize()</code>、<code>ZonedDateTime.parse()</code>、<code>serialization.deserialize()</code> 验证 <code>JsonMQEventSerialization</code> 的运行行为。
 */
class JsonMQEventSerializationTest {

    /**
     * 验证<code>serialization.serialize(new SelfReference())</code> 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void rejectsPayloadThatCannotBeSerialized() {
        JsonMQEventSerialization serialization = new JsonMQEventSerialization();
        assertThrows(IllegalStateException.class, () -> serialization.serialize(new SelfReference()));
    }

    /**
     * 验证<code>restored.eventTimestamp</code> 等于 <code>source.eventTimestamp</code>。
     */
    @Test
    void preservesDomainEventTimestamp() {
        JsonMQEventSerialization serialization = new JsonMQEventSerialization();
        TimestampPayload source = new TimestampPayload();
        source.eventTimestamp = ZonedDateTime.parse("2026-10-15T12:34:56+08:00[Asia/Shanghai]");

        String json = serialization.serialize(source);
        assertEquals("{\"eventTimestamp\":\"2026-10-15T12:34:56+08:00\"}", json);
        TimestampPayload restored = serialization.deserialize(json, TimestampPayload.class);

        assertEquals(source.eventTimestamp, restored.eventTimestamp);
    }

    /**
     * 将 <code>eventTimestamp</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    public static final class TimestampPayload {
        /**
         * 用于验证事件时间读写一致性的时间戳。
         */
        public ZonedDateTime eventTimestamp;
    }

    /**
     * 提供 <code>getSelf()</code> 等供当前测试使用的独立操作。
     */
    private static final class SelfReference {
        /**
         * 读取 <code>this</code> 供当前测试使用。
         *
         * @return 当前测试使用的结果，取自 <code>this</code>
         */
        public SelfReference getSelf() {
            return this;
        }
    }
}
