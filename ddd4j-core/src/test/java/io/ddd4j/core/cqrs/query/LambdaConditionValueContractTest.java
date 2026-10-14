package io.ddd4j.core.cqrs.query;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LambdaConditionValueContractTest {

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

    @Test
    void javaDeserializationRejectsInvalidCanonicalState() throws Exception {
        LambdaCondition condition = condition();
        Field operator = LambdaCondition.class.getDeclaredField("operator");
        operator.setAccessible(true);
        operator.set(condition, null);

        assertThatThrownBy(() -> roundTrip(condition)).isInstanceOf(InvalidObjectException.class);
    }

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

    @Test
    void hashCodeUsesTheSameComponentOrderAcrossLines() {
        LambdaCondition condition = condition();
        int expected = condition.propertyRef().hashCode();
        expected = 31 * expected + condition.operator().hashCode();
        expected = 31 * expected + condition.value().hashCode();

        assertThat(condition.hashCode()).isEqualTo(expected);
        assertThat(condition.hashCode()).isEqualTo(condition().hashCode());
    }

    @Test
    void propertyReferenceDescriptionIsStableAcrossLines() {
        assertThat(condition().propertyRef().toString()).isEqualTo(
                "PropertyRef[space=DOMAIN, ownerType=class java.lang.String, property=status]");
    }

    private static LambdaCondition condition() {
        return new LambdaCondition(new PropertyRef(PropertySpace.DOMAIN, String.class, "status"), "=", "ACTIVE");
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
