package io.ddd4j.core.health;

import io.ddd4j.core.cqrs.query.PropertyRef;
import io.ddd4j.core.cqrs.query.PropertySpace;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.ObjectStreamClass;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.InvalidObjectException;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlainValueContractTest {
    @Test
    void frameworkValuesAreFinalOrdinaryClasses() {
        assertThat(ReadinessResult.class.getSuperclass()).isEqualTo(Object.class);
        assertThat(Modifier.isFinal(ReadinessResult.class.getModifiers())).isTrue();
        assertThat(PropertyRef.class.getSuperclass()).isEqualTo(Object.class);
        assertThat(ObjectStreamClass.lookup(PropertyRef.class).getSerialVersionUID()).isZero();
    }

    @Test
    void componentsPreserveValuesAndDefensiveCopies() {
        Map<String, String> details = new HashMap<>();
        details.put("reason", "warming up");
        ReadinessResult result = new ReadinessResult("db", false, details);
        details.put("reason", "changed");
        ReadinessResult equal = new ReadinessResult("db", false, Map.of("reason", "warming up"));
        assertThat(result).isEqualTo(equal);
        assertThat(result.hashCode()).isEqualTo(equal.hashCode());
        assertThat(result.toString()).isEqualTo("ReadinessResult[name=db, ready=false, details={reason=warming up}]");
        assertThat(result.name()).isEqualTo("db");
        assertThat(result.details()).containsEntry("reason", "warming up");
        assertThatThrownBy(() -> result.details().put("new", "value")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new PropertyRef(PropertySpace.DOMAIN, String.class, "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void javaSerializationPreservesCanonicalValidation() throws Exception {
        PropertyRef valid = new PropertyRef(PropertySpace.DOMAIN, String.class, "length");
        assertThat(roundTrip(valid)).isEqualTo(valid);
        var field = PropertyRef.class.getDeclaredField("property");
        field.setAccessible(true);
        field.set(valid, "");
        assertThatThrownBy(() -> roundTrip(valid)).isInstanceOf(InvalidObjectException.class);
    }

    private Object roundTrip(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return input.readObject();
        }
    }

    @Test
    void jacksonPreservesComponentNamesAndCanonicalValidation() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        ReadinessResult value = new ReadinessResult("db", true, Map.of("version", "1"));
        String json = mapper.writeValueAsString(value);
        assertThat(mapper.readTree(json).properties()).hasSize(3);
        assertThat(mapper.readValue(json, ReadinessResult.class)).isEqualTo(value);
        assertThat(mapper.readValue("{\"name\":\"db\",\"ready\":true,\"details\":null}", ReadinessResult.class).details()).isEmpty();
    }
}
