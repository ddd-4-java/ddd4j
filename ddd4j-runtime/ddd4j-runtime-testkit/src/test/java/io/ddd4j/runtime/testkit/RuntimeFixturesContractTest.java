package io.ddd4j.runtime.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** \u901a\u8fc7 <code>contributors.size()</code>\u3001<code>contributors.get()</code>\u3001<code>contributors.set()</code> \u9a8c\u8bc1 <code>RuntimeFixturesContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class RuntimeFixturesContractTest {

    /** \u9a8c\u8bc1<code>contributors.size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>contributors.get(0).check().name()</code> \u7b49\u4e8e <code>"runtime-fixture"</code>\uff0c<code>contributors.set(0, contributors.get(0))</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
    @Test
    void readinessContributors_areStableAndCannotBeModified() {
        List<ReadinessContributor> contributors = new RuntimeFixtures().readinessContributors();

        assertEquals(1, contributors.size());
        assertEquals("runtime-fixture", contributors.get(0).check().name());
        assertThrows(UnsupportedOperationException.class,
                () -> contributors.set(0, contributors.get(0)));
    }

    /** \u9a8c\u8bc1<code>services.size()</code> \u7b49\u4e8e <code>4</code>\uff0c<code>services.get(SpiKeys.DOMAIN_EVENT_PUBLISHER)</code> \u7b49\u4e8e <code>DomainEventPublisher.class</code>\uff0c<code>services.put("extra", Object.class)</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
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
