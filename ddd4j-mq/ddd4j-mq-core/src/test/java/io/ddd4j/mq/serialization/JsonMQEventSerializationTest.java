package io.ddd4j.mq.serialization;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonMQEventSerializationTest {

    @Test
    void rejectsPayloadThatCannotBeSerialized() {
        JsonMQEventSerialization serialization = new JsonMQEventSerialization();
        assertThrows(IllegalStateException.class, () -> serialization.serialize(new SelfReference()));
    }

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

    public static final class TimestampPayload {
        public ZonedDateTime eventTimestamp;
    }

    private static final class SelfReference {
        public SelfReference getSelf() {
            return this;
        }
    }
}
