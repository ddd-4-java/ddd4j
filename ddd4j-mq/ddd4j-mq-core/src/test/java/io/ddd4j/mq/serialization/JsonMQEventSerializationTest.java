package io.ddd4j.mq.serialization;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** \u901a\u8fc7 <code>serialization.serialize()</code>\u3001<code>ZonedDateTime.parse()</code>\u3001<code>serialization.deserialize()</code> \u9a8c\u8bc1 <code>JsonMQEventSerialization</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class JsonMQEventSerializationTest {

    /** \u9a8c\u8bc1<code>serialization.serialize(new SelfReference())</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void rejectsPayloadThatCannotBeSerialized() {
        JsonMQEventSerialization serialization = new JsonMQEventSerialization();
        assertThrows(IllegalStateException.class, () -> serialization.serialize(new SelfReference()));
    }

    /** \u9a8c\u8bc1<code>restored.eventTimestamp</code> \u7b49\u4e8e <code>source.eventTimestamp</code>\u3002 */
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

    /** \u5c06 <code>eventTimestamp</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    public static final class TimestampPayload {
        /** \u7528\u4e8e\u9a8c\u8bc1\u4e8b\u4ef6\u65f6\u95f4\u8bfb\u5199\u4e00\u81f4\u6027\u7684\u65f6\u95f4\u6233\u3002 */
        public ZonedDateTime eventTimestamp;
    }

    /** \u63d0\u4f9b <code>getSelf()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    private static final class SelfReference {
        /**
         * \u8bfb\u53d6 <code>this</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>this</code>
         */
        public SelfReference getSelf() {
            return this;
        }
    }
}
