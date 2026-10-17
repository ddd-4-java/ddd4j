package io.ddd4j.runtime.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeFixturesContractTest {

    @Test
    void readinessContributors_areStableAndCannotBeModified() {
        List<ReadinessContributor> contributors = new RuntimeFixtures().readinessContributors();

        assertEquals(1, contributors.size());
        assertEquals("runtime-fixture", contributors.get(0).check().name());
        assertThrows(UnsupportedOperationException.class,
                () -> contributors.set(0, contributors.get(0)));
    }

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
