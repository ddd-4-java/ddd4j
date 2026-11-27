package io.ddd4j.runtime.testkit;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.health.ReadinessContributor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 通过 <code>contributors.size()</code>、<code>contributors.get()</code>、<code>contributors.set()</code> 验证 <code>RuntimeFixturesContract</code> 的运行行为。
 */
class RuntimeFixturesContractTest {

    /**
     * 验证<code>contributors.size()</code> 等于 <code>1</code>，<code>contributors.get(0).check().name()</code> 等于 <code>"runtime-fixture"</code>，<code>contributors.set(0, contributors.get(0))</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void readinessContributors_areStableAndCannotBeModified() {
        List<ReadinessContributor> contributors = new RuntimeFixtures().readinessContributors();

        assertEquals(1, contributors.size());
        assertEquals("runtime-fixture", contributors.get(0).check().name());
        assertThrows(UnsupportedOperationException.class,
                () -> contributors.set(0, contributors.get(0)));
    }

    /**
     * 验证<code>services.size()</code> 等于 <code>4</code>，<code>services.get(SpiKeys.DOMAIN_EVENT_PUBLISHER)</code> 等于 <code>DomainEventPublisher.class</code>，<code>services.put("extra", Object.class)</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void serviceRegistry_hasFourStableKeysAndCannotBeModified() {
        Map<String, Class<?>> services = new RuntimeFixtures().services();

        assertEquals(4, services.size());
        assertEquals(DomainEventPublisher.class,
                services.get(SpiKeys.DOMAIN_EVENT_PUBLISHER));
        assertThrows(UnsupportedOperationException.class,
                () -> services.put("extra", Object.class));
    }
}
