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
     * 通过 <code>input.readObject()</code> 取得当前测试需要的结果。
     *
     * @param value 当前用例传入的值
     * @return 当前测试使用的结果，取自 <code>input.readObject()</code>
     * @throws Exception 准备或读取夹具文件时失败
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
     * 验证<code>roundTrip(property)</code> 等于 <code>property</code>，<code>roundTrip(condition)</code> 等于 <code>condition</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
