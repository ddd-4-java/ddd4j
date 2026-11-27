package io.ddd4j.core.cqrs.query;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 查询属性和值条件的 Java 序列化契约。
 */
class QuerySerializationContractTest {

    /**
     * \u901a\u8fc7 <code>input.readObject()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param value \u5f53\u524d\u7528\u4f8b\u4f20\u5165\u7684\u503c
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>input.readObject()</code>
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
     */
    private static Object roundTrip(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return input.readObject();
        }
    }

    /**
     * \u9a8c\u8bc1<code>roundTrip(property)</code> \u7b49\u4e8e <code>property</code>\uff0c<code>roundTrip(condition)</code> \u7b49\u4e8e <code>condition</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void queryValuesRoundTripWithoutChangingComponents() throws Exception {
        PropertyRef property = new PropertyRef(PropertySpace.DOMAIN, String.class, "status");
        LambdaCondition condition = new LambdaCondition(property, "=", "ACTIVE");

        assertEquals(property, roundTrip(property));
        assertEquals(condition, roundTrip(condition));
        assertEquals(property.hashCode(), roundTrip(property).hashCode());
        assertEquals(condition.hashCode(), roundTrip(condition).hashCode());
    }
}
