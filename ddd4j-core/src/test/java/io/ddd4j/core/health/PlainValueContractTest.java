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

/** \u901a\u8fc7 <code>details.put()</code>\u3001<code>result.details()</code>\u3001<code>ReadinessResult.class.getSuperclass()</code> \u9a8c\u8bc1 <code>PlainValueContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class PlainValueContractTest {
    /** \u9a8c\u8bc1<code>ReadinessResult.class.getSuperclass()</code> \u7b49\u4e8e <code>Object.class</code>\uff0c<code>Modifier.isFinal(ReadinessResult.class.getModifiers())</code>\u4e3a\u771f\uff0c<code>ObjectStreamClass.lookup(LambdaCondition.class).getSerialVersionUID()</code> \u7b49\u4e8e <code>-4292780888118581043L</code>\u3002 */
    @Test
    void frameworkValuesAreFinalOrdinaryClasses() {
        assertThat(ReadinessResult.class.getSuperclass()).isEqualTo(Object.class);
        assertThat(Modifier.isFinal(ReadinessResult.class.getModifiers())).isTrue();
        assertThat(PropertyRef.class.getSuperclass()).isEqualTo(Object.class);
        assertThat(ObjectStreamClass.lookup(PropertyRef.class).getSerialVersionUID()).isZero();
    }

    /** \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>equal</code>\uff0c<code>result.hashCode()</code> \u7b49\u4e8e <code>equal.hashCode()</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
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

    /**
     * \u9a8c\u8bc1<code>roundTrip(valid)</code> \u7b49\u4e8e <code>valid</code>\uff0c<code>roundTrip(valid)</code> \u629b\u51fa <code>InvalidObjectException.class</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
    @Test
    void javaSerializationPreservesCanonicalValidation() throws Exception {
        PropertyRef valid = new PropertyRef(PropertySpace.DOMAIN, String.class, "length");
        assertThat(roundTrip(valid)).isEqualTo(valid);
        var field = PropertyRef.class.getDeclaredField("property");
        field.setAccessible(true);
        field.set(valid, "");
        assertThatThrownBy(() -> roundTrip(valid)).isInstanceOf(InvalidObjectException.class);
    }

    /**
     * \u901a\u8fc7 <code>input.readObject()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param value \u5f53\u524d\u7528\u4f8b\u4f20\u5165\u7684\u503c
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>input.readObject()</code>
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
     */
    private Object roundTrip(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return input.readObject();
        }
    }

    /**
     * \u9a8c\u8bc1<code>mapper.readTree(json).properties()</code> \u7684\u5927\u5c0f\u4e3a <code>3</code>\uff0c<code>mapper.readValue(json, ReadinessResult.class)</code> \u7b49\u4e8e <code>value</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
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
