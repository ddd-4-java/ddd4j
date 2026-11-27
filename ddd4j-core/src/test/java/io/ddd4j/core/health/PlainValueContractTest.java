package io.ddd4j.core.health;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.cqrs.query.LambdaCondition;
import io.ddd4j.core.cqrs.query.PropertyRef;
import io.ddd4j.core.cqrs.query.PropertySpace;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 通过 <code>details.put()</code>、<code>result.details()</code>、<code>ReadinessResult.class.getSuperclass()</code> 验证 <code>PlainValueContract</code> 的运行行为。
 */
class PlainValueContractTest {
    /**
     * 在测试框架中执行 <code>details.put()</code>、<code>result.details()</code>，验证该场景。
     */
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

    /**
     * 验证<code>ReadinessResult.class.getSuperclass()</code> 等于 <code>Object.class</code>，<code>Modifier.isFinal(ReadinessResult.class.getModifiers())</code>为真，<code>ObjectStreamClass.lookup(LambdaCondition.class).getSerialVersionUID()</code> 等于 <code>-4292780888118581043L</code>。
     */
    @Test
    void frameworkValuesAreFinalOrdinaryClasses() {
        assertThat(ReadinessResult.class.getSuperclass()).isEqualTo(Object.class);
        assertThat(Modifier.isFinal(ReadinessResult.class.getModifiers())).isTrue();
        assertThat(PropertyRef.class.getSuperclass()).isEqualTo(Object.class);
        assertThat(ObjectStreamClass.lookup(PropertyRef.class).getSerialVersionUID())
                .isEqualTo(-3906849612189306727L);
        assertThat(ObjectStreamClass.lookup(LambdaCondition.class).getSerialVersionUID())
                .isEqualTo(-4292780888118581043L);
    }

    /**
     * 验证<code>result</code> 等于 <code>equal</code>，<code>result.hashCode()</code> 等于 <code>equal.hashCode()</code>，被测调用 抛出 <code>IllegalArgumentException.class</code>。
     */
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
     * 验证<code>ready.hashCode()</code> 等于 <code>31 * (31 * "db".hashCode() + Boolean.hashCode(true)) + ready.details().hashCode()</code>，<code>unavailable.hashCode()</code> 等于 <code>31 * (31 * "db".hashCode() + Boolean.hashCode(false)) + unavailable.details().hashCode()</code>。
     */
    @Test
    void readinessResultHashUsesTheSameBooleanValueAcrossLines() {
        ReadinessResult ready = ReadinessResult.ready("db");
        ReadinessResult unavailable = ReadinessResult.unavailable("db", null);

        assertThat(ready.hashCode()).isEqualTo(
                31 * (31 * "db".hashCode() + Boolean.hashCode(true)) + ready.details().hashCode());
        assertThat(unavailable.hashCode()).isEqualTo(
                31 * (31 * "db".hashCode() + Boolean.hashCode(false)) + unavailable.details().hashCode());
    }

    /**
     * 验证<code>ready.hashCode()</code> 等于 <code>31 * Boolean.hashCode(true) + ready.results().hashCode()</code>，<code>unavailable.hashCode()</code> 等于 <code>31 * Boolean.hashCode(false) + unavailable.results().hashCode()</code>。
     */
    @Test
    void readinessReportHashUsesTheSameBooleanValueAcrossLines() {
        ReadinessReport ready = new ReadinessReport(true, Collections.emptyList());
        ReadinessReport unavailable = new ReadinessReport(false, Collections.emptyList());

        assertThat(ready.hashCode()).isEqualTo(
                31 * Boolean.hashCode(true) + ready.results().hashCode());
        assertThat(unavailable.hashCode()).isEqualTo(
                31 * Boolean.hashCode(false) + unavailable.results().hashCode());
    }

    /**
     * 验证<code>roundTrip(valid)</code> 等于 <code>valid</code>，<code>roundTrip(valid)</code> 抛出 <code>InvalidObjectException.class</code>。
     *
     * @throws Exception 反射访问被测成员时失败
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
     * 通过 <code>input.readObject()</code> 取得当前测试需要的结果。
     *
     * @param value 当前用例传入的值
     * @return 当前测试使用的结果，取自 <code>input.readObject()</code>
     * @throws Exception 准备或读取夹具文件时失败
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
     * 验证<code>mapper.readTree(json).properties()</code> 的大小为 <code>3</code>，<code>mapper.readValue(json, ReadinessResult.class)</code> 等于 <code>value</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
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

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void immutableValueCollectionsRejectNullEntries() {
        assertThatThrownBy(() -> new ReadinessResult("db", true,
                Collections.singletonMap("nullable", null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReadinessReport(true,
                Arrays.asList(ReadinessResult.ready("db"), null)))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * 验证<code>mapper.readTree(json).get("ready").asBoolean()</code>为真，<code>mapper.readValue(json, ReadinessReport.class)</code> 等于 <code>report</code>，<code>report.toString()</code> 等于 <code>"ReadinessReport[ready=true, results=[ReadinessResult[name=db, ready=true, details={}]]]"</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
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
