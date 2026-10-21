package io.ddd4j.sample.order.domain;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneySerializationContractTest {

    @Test
    void preservesPublishedSerialVersionUidAndValue() throws Exception {
        assertEquals("1.8".equals(System.getProperty("java.specification.version")) ? -8848492078636310279L : 0L,
                ObjectStreamClass.lookup(Money.class).getSerialVersionUID());

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
