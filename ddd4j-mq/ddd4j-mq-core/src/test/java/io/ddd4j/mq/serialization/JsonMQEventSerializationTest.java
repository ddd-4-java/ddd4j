package io.ddd4j.mq.serialization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonMQEventSerializationTest {

    @Test
    void rejectsPayloadThatCannotBeSerialized() {
        JsonMQEventSerialization serialization = new JsonMQEventSerialization();
        assertThrows(IllegalStateException.class, () -> serialization.serialize(new SelfReference()));
    }

    private static final class SelfReference {
        public SelfReference getSelf() {
            return this;
        }
    }
}
