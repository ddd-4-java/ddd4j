package io.ddd4j.core.cqrs.query;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** \u9a8c\u8bc1 <code>LambdaConditionValueContract</code> \u5728 <code>javaDeserialization</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class LambdaConditionValueContractTest {

    /**
     * \u521b\u5efa <code>new LambdaCondition</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new LambdaCondition(new PropertyRef(PropertySpace.DOMAIN, String.class, "status"), "=", "ACTIVE")</code>
     */
    private static LambdaCondition condition() {
        return new LambdaCondition(new PropertyRef(PropertySpace.DOMAIN, String.class, "status"), "=", "ACTIVE");
    }

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
     * \u9a8c\u8bc1<code>mapper.readTree(json).size()</code> \u7b49\u4e8e <code>3</code>\uff0c<code>mapper.readTree(json).get("operator").asText()</code> \u7b49\u4e8e <code>"="</code>\uff0c<code>mapper.readValue(json, LambdaCondition.class)</code> \u7b49\u4e8e <code>condition</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>roundTrip(condition)</code> \u629b\u51fa <code>InvalidObjectException.class</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
    @Test
    void javaDeserializationRejectsInvalidCanonicalState() throws Exception {
        LambdaCondition condition = condition();
        Field operator = LambdaCondition.class.getDeclaredField("operator");
        operator.setAccessible(true);
        operator.set(condition, null);

        assertThatThrownBy(() -> roundTrip(condition)).isInstanceOf(InvalidObjectException.class);
    }

    /** \u9a8c\u8bc1<code>condition.property()</code> \u7b49\u4e8e <code>"status"</code>\uff0c<code>condition.getPropertyRef()</code> \u7b49\u4e8e <code>condition.propertyRef()</code>\uff0c<code>condition.toString()</code> \u7b49\u4e8e <code>"LambdaCondition[propertyRef=" + condition.propertyRef() + ", operator==, value=ACTIVE]"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>condition.hashCode()</code> \u7b49\u4e8e <code>expected</code>\uff0c<code>condition.hashCode()</code> \u7b49\u4e8e <code>condition().hashCode()</code>\u3002 */
    @Test
    void hashCodeUsesTheSameComponentOrderAcrossLines() {
        LambdaCondition condition = condition();
        int expected = condition.propertyRef().hashCode();
        expected = 31 * expected + condition.operator().hashCode();
        expected = 31 * expected + condition.value().hashCode();

        assertThat(condition.hashCode()).isEqualTo(expected);
        assertThat(condition.hashCode()).isEqualTo(condition().hashCode());
    }

    /** \u9a8c\u8bc1<code>condition().propertyRef().toString()</code> \u7b49\u4e8e <code>"PropertyRef[space=DOMAIN, ownerType=class java.lang.String, property=status]"</code>\u3002 */
    @Test
    void propertyReferenceDescriptionIsStableAcrossLines() {
        assertThat(condition().propertyRef().toString()).isEqualTo(
                "PropertyRef[space=DOMAIN, ownerType=class java.lang.String, property=status]");
    }
}
