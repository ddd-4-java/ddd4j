package io.ddd4j.runtime.testkit;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/** \u901a\u8fc7 <code>withNullKey.put()</code>\u3001<code>adapter()</code>\u3001<code>withNullValue.put()</code> \u9a8c\u8bc1 <code>RuntimeContractAdapter</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class RuntimeContractAdapterTest {

    /** \u9a8c\u8bc1<code>adapter(withNullKey)</code> \u629b\u51fa <code>NullPointerException</code>\uff0c<code>adapter(withNullValue)</code> \u629b\u51fa <code>NullPointerException</code>\u3002 */
    @Test
    void servicesRejectNullKeysAndValues() {
        Map<String, Class<?>> withNullKey = new HashMap<>();
        withNullKey.put(null, String.class);
        assertThatNullPointerException().isThrownBy(() -> adapter(withNullKey));

        Map<String, Class<?>> withNullValue = new HashMap<>();
        withNullValue.put("missing", null);
        assertThatNullPointerException().isThrownBy(() -> adapter(withNullValue));
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>source.put()</code>\u3001<code>adapter()</code>\u3001<code>adapter.services()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void servicesAreAnUnmodifiableSnapshot() {
        Map<String, Class<?>> source = new HashMap<>();
        source.put("service", String.class);
        RuntimeContractAdapter adapter = adapter(source);
        source.put("later", Integer.class);

        assertThat(adapter.services()).containsOnlyKeys("service");
        assertThat(adapter.services()).isUnmodifiable();
    }

    /**
     * \u521b\u5efa <code>new RuntimeContractAdapter</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param services \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>Map&lt;String, Class&lt;?&gt;&gt;</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new RuntimeContractAdapter(()-&gt;{ }, ()-&gt;{ }, services, ()-&gt;null)</code>
     */
    private RuntimeContractAdapter adapter(Map<String, Class<?>> services) {
        return new RuntimeContractAdapter(() -> {
        }, () -> {
        }, services, () -> null);
    }
}
