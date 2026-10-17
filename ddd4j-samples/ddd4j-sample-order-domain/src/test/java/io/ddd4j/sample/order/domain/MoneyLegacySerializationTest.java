package io.ddd4j.sample.order.domain;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyLegacySerializationTest {

    @Test
    void preservesJdk8PublishedSerialVersionUidAndValue() throws Exception {
        assertEquals(-8848492078636310279L, ObjectStreamClass.lookup(Money.class).getSerialVersionUID());

        Money original = Money.cny(new BigDecimal("12.35"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(original, input.readObject());
        }
    }
}
