package io.ddd4j.sample.javalin.cqrs.order.domain.model;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyLegacySerializationTest {

    @Test
    void preservesJdk8PublishedSerialVersionUidAndValue() throws Exception {
        assertEquals(-8477704881210516506L, ObjectStreamClass.lookup(Money.class).getSerialVersionUID());

        Money original = Money.cny("3.50");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(original, input.readObject());
        }
    }
}
