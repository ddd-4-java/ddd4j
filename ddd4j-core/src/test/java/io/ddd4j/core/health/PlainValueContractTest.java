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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlainValueContractTest {
    @Test
    void diagnosticDetailsKeepInputOrder() {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("alpha", "1");
        details.put("beta", "2");
        details.put("gamma", "3");
        details.put("delta", "4");
        details.put("epsilon", "5");
        ReadinessResult result = new ReadinessResult("db", true, details);
        assertThat(new ArrayList<>(result.details().keySet()))
                .containsExactly("alpha", "beta", "gamma", "delta", "epsilon");
    }

    @Test
    void frameworkValuesAreFinalOrdinaryClasses() {
        assertThat(ReadinessResult.class.getSuperclass()).isEqualTo(Object.class);
        assertThat(Modifier.isFinal(ReadinessResult.class.getModifiers())).isTrue();
        assertThat(PropertyRef.class.getSuperclass()).isEqualTo(Object.class);
        // 1.0.x 已有 Java 序列化数据保留原 UID，避免旧流反序列化失败。
        assertThat(ObjectStreamClass.lookup(PropertyRef.class).getSerialVersionUID())
                .isEqualTo(-3906849612189306727L);
    }

    @Test
    void componentsPreserveValuesAndDefensiveCopies() {
        Map<String, String> details = new HashMap<>();
        details.put("reason", "warming up");
        ReadinessResult result = new ReadinessResult("db", false, details);
        details.put("reason", "changed");
        ReadinessResult equal = new ReadinessResult("db", false, Collections.singletonMap("reason", "warming up"));
        assertThat(result).isEqualTo(equal);
        assertThat(result.hashCode()).isEqualTo(equal.hashCode());
        assertThat(result.toString()).isEqualTo("ReadinessResult[name=db, ready=false, details={reason=warming up}]");
        assertThat(result.name()).isEqualTo("db");
        assertThat(result.details()).containsEntry("reason", "warming up");
        assertThatThrownBy(() -> result.details().put("new", "value")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new PropertyRef(PropertySpace.DOMAIN, String.class, "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readinessResultHashUsesTheSameBooleanValueAcrossLines() {
        ReadinessResult ready = ReadinessResult.ready("db");
        ReadinessResult unavailable = ReadinessResult.unavailable("db", null);

        assertThat(ready.hashCode()).isEqualTo(
                31 * (31 * "db".hashCode() + Boolean.hashCode(true)) + ready.details().hashCode());
        assertThat(unavailable.hashCode()).isEqualTo(
                31 * (31 * "db".hashCode() + Boolean.hashCode(false)) + unavailable.details().hashCode());
    }

    @Test
    void javaSerializationPreservesCanonicalValidation() throws Exception {
        PropertyRef valid = new PropertyRef(PropertySpace.DOMAIN, String.class, "length");
        assertThat(roundTrip(valid)).isEqualTo(valid);
        Field field = PropertyRef.class.getDeclaredField("property");
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
        ReadinessResult value = new ReadinessResult("db", true, Collections.singletonMap("version", "1"));
        String json = mapper.writeValueAsString(value);
        assertThat(mapper.readTree(json).size()).isEqualTo(3);
        assertThat(mapper.readValue(json, ReadinessResult.class)).isEqualTo(value);
        assertThat(mapper.readValue("{\"name\":\"db\",\"ready\":true,\"details\":null}", ReadinessResult.class).details()).isEmpty();
    }

    @Test
    void immutableValueCollectionsRejectNullEntries() {
        assertThatThrownBy(() -> new ReadinessResult("db", true,
                Collections.singletonMap("nullable", null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReadinessReport(true,
                Arrays.asList(ReadinessResult.ready("db"), null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void readinessReportRetainsValueAndJsonContract() throws Exception {
        ReadinessReport report = new ReadinessReport(true,
                Collections.singletonList(ReadinessResult.ready("db")));
        JsonMapper mapper = JsonMapper.builder().build();
        String json = mapper.writeValueAsString(report);
        assertThat(mapper.readTree(json).get("ready").asBoolean()).isTrue();
        assertThat(mapper.readValue(json, ReadinessReport.class)).isEqualTo(report);
        assertThat(report.toString()).isEqualTo(
                "ReadinessReport[ready=true, results=[ReadinessResult[name=db, ready=true, details={}]]]");
    }
}
