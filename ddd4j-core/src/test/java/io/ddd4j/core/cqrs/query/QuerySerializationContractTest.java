package io.ddd4j.core.cqrs.query;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 查询属性和值条件的 Java 序列化契约。 */
class QuerySerializationContractTest {

    @Test
    void queryValuesRoundTripWithoutChangingComponents() throws Exception {
        PropertyRef property = new PropertyRef(PropertySpace.DOMAIN, String.class, "status");
        LambdaCondition condition = new LambdaCondition(property, "=", "ACTIVE");

        assertEquals(property, roundTrip(property));
        assertEquals(condition, roundTrip(condition));
        assertEquals(property.hashCode(), roundTrip(property).hashCode());
        assertEquals(condition.hashCode(), roundTrip(condition).hashCode());
    }

    private static Object roundTrip(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return input.readObject();
        }
    }
}
