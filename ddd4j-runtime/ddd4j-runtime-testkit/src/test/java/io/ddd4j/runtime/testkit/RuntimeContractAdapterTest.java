package io.ddd4j.runtime.testkit;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * 通过 <code>withNullKey.put()</code>、<code>adapter()</code>、<code>withNullValue.put()</code> 验证 <code>RuntimeContractAdapter</code> 的运行行为。
 */
class RuntimeContractAdapterTest {

    /**
     * 验证<code>adapter(withNullKey)</code> 抛出 <code>NullPointerException</code>，<code>adapter(withNullValue)</code> 抛出 <code>NullPointerException</code>。
     */
    @Test
    void servicesRejectNullKeysAndValues() {
        Map<String, Class<?>> withNullKey = new HashMap<>();
        withNullKey.put(null, String.class);
        assertThatNullPointerException().isThrownBy(() -> adapter(withNullKey));

        Map<String, Class<?>> withNullValue = new HashMap<>();
        withNullValue.put("missing", null);
        assertThatNullPointerException().isThrownBy(() -> adapter(withNullValue));
    }

    /**
     * 在测试框架中执行 <code>source.put()</code>、<code>adapter()</code>、<code>adapter.services()</code>，验证该场景。
     */
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
     * 创建 <code>new RuntimeContractAdapter</code> 供当前测试路径调用。
     *
     * @param services 用于构造本次返回结果的 <code>Map&lt;String, Class&lt;?&gt;&gt;</code> 输入
     * @return 当前测试使用的结果，取自 <code>new RuntimeContractAdapter(()-&gt;{ }, ()-&gt;{ }, services, ()-&gt;null)</code>
     */
    private RuntimeContractAdapter adapter(Map<String, Class<?>> services) {
        return new RuntimeContractAdapter(() -> {
        }, () -> {
        }, services, () -> null);
    }
}
