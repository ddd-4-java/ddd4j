package io.ddd4j.runtime.testkit;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RuntimeContractAdapterTest {

    @Test
    void servicesRejectNullKeysAndValues() {
        Map<String, Class<?>> withNullKey = new HashMap<>();
        withNullKey.put(null, String.class);
        assertThatNullPointerException().isThrownBy(() -> adapter(withNullKey));

        Map<String, Class<?>> withNullValue = new HashMap<>();
        withNullValue.put("missing", null);
        assertThatNullPointerException().isThrownBy(() -> adapter(withNullValue));
    }

    @Test
    void servicesAreAnUnmodifiableSnapshot() {
        Map<String, Class<?>> source = new HashMap<>();
        source.put("service", String.class);
        RuntimeContractAdapter adapter = adapter(source);
        source.put("later", Integer.class);

        assertThat(adapter.services()).containsOnlyKeys("service");
        assertThat(adapter.services()).isUnmodifiable();
    }

    private RuntimeContractAdapter adapter(Map<String, Class<?>> services) {
        return new RuntimeContractAdapter(() -> { }, () -> { }, services, () -> null);
    }
}
