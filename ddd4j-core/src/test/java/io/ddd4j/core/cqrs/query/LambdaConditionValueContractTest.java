package io.ddd4j.core.cqrs.query;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 <code>LambdaConditionValueContract</code> 在 <code>javaDeserialization</code> 等场景中的行为。
 */
class LambdaConditionValueContractTest {

    /**
     * 创建 <code>new LambdaCondition</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new LambdaCondition(new PropertyRef(PropertySpace.DOMAIN, String.class, "status"), "=", "ACTIVE")</code>
     */
    private static LambdaCondition condition() {
        return new LambdaCondition(new PropertyRef(PropertySpace.DOMAIN, String.class, "status"), "=", "ACTIVE");
    }

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
     * 验证<code>mapper.readTree(json).size()</code> 等于 <code>3</code>，<code>mapper.readTree(json).get("operator").asText()</code> 等于 <code>"="</code>，<code>mapper.readValue(json, LambdaCondition.class)</code> 等于 <code>condition</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void jsonRoundTripPreservesComponents() throws Exception {
        LambdaCondition condition = condition();
        JsonMapper mapper = JsonMapper.builder().build();

        String json = mapper.writeValueAsString(condition);

        assertThat(mapper.readTree(json).size()).isEqualTo(3);
        assertThat(mapper.readTree(json).get("operator").asText()).isEqualTo("=");
        assertThat(mapper.readTree(json).get("value").asText()).isEqualTo("ACTIVE");
        assertThat(mapper.readValue(json, LambdaCondition.class)).isEqualTo(condition);
    }

    /**
     * 验证<code>roundTrip(condition)</code> 抛出 <code>InvalidObjectException.class</code>。
     *
     * @throws Exception 反射访问被测成员时失败
     */
    @Test
    void javaDeserializationRejectsInvalidCanonicalState() throws Exception {
        LambdaCondition condition = condition();
        Field operator = LambdaCondition.class.getDeclaredField("operator");
        operator.setAccessible(true);
        operator.set(condition, null);

        assertThatThrownBy(() -> roundTrip(condition)).isInstanceOf(InvalidObjectException.class);
    }

    /**
     * 验证<code>condition.property()</code> 等于 <code>"status"</code>，<code>condition.getPropertyRef()</code> 等于 <code>condition.propertyRef()</code>，<code>condition.toString()</code> 等于 <code>"LambdaCondition[propertyRef=" + condition.propertyRef() + ", operator==, value=ACTIVE]"</code>。
     */
    @Test
    void valueDescriptionAndAccessorsAreStable() {
        LambdaCondition condition = condition();
        assertThat(condition.property()).isEqualTo("status");
        assertThat(condition.getPropertyRef()).isEqualTo(condition.propertyRef());
        assertThat(condition.getOperator()).isEqualTo(condition.operator());
        assertThat(condition.getValue()).isEqualTo(condition.value());
        assertThat(condition.toString()).isEqualTo(
                "LambdaCondition[propertyRef=" + condition.propertyRef() + ", operator==, value=ACTIVE]");
    }

    /**
     * 验证<code>condition.hashCode()</code> 等于 <code>expected</code>，<code>condition.hashCode()</code> 等于 <code>condition().hashCode()</code>。
     */
    @Test
    void hashCodeUsesTheSameComponentOrderAcrossLines() {
        LambdaCondition condition = condition();
        int expected = condition.propertyRef().hashCode();
        expected = 31 * expected + condition.operator().hashCode();
        expected = 31 * expected + condition.value().hashCode();

        assertThat(condition.hashCode()).isEqualTo(expected);
        assertThat(condition.hashCode()).isEqualTo(condition().hashCode());
    }

    /**
     * 验证<code>condition().propertyRef().toString()</code> 等于 <code>"PropertyRef[space=DOMAIN, ownerType=class java.lang.String, property=status]"</code>。
     */
    @Test
    void propertyReferenceDescriptionIsStableAcrossLines() {
        assertThat(condition().propertyRef().toString()).isEqualTo(
                "PropertyRef[space=DOMAIN, ownerType=class java.lang.String, property=status]");
    }
}
