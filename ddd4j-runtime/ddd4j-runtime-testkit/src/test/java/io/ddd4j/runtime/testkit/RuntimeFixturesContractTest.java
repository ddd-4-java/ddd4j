package io.ddd4j.runtime.testkit;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.health.ReadinessContributor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
