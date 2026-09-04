package io.ddd4j.data.event.store.esdb;

import java.util.Arrays;
import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** EventStoreDB 的强类型 Testcontainers 验证。 */
@Testcontainers(disabledWithoutDocker = true)
class EsdbEventStoreIT {
    private static final String ORDER_TYPE = "Order";
    @Container static final GenericContainer<?> ESDB = new GenericContainer<>(DockerImageName.parse("eventstore/eventstore:24.10.0-bookworm-slim"))
            .withExposedPorts(2113)
            .withEnv("EVENTSTORE_CLUSTER_SIZE", "1")
            .withEnv("EVENTSTORE_RUN_PROJECTIONS", "All")
            .withEnv("EVENTSTORE_START_STANDARD_PROJECTIONS", "true")
            .withEnv("EVENTSTORE_INSECURE", "true")
            .withEnv("EVENTSTORE_MEM_DB", "true")
            .waitingFor(Wait.forHttp("/health/live").forPort(2113)
                    .forStatusCodeMatching(status -> status >= 200 && status < 300))
            .withStartupTimeout(Duration.ofMinutes(2));
    private static EventStoreDBClient client;
    private EventStore store;

    @BeforeAll static void createClient() {
        client = EventStoreDBClient.create(EventStoreDBConnectionString.parseOrThrow(
                "esdb://" + ESDB.getHost() + ":" + ESDB.getMappedPort(2113)
                        + "?tls=false&maxDiscoverAttempts=3"));
    }
    @AfterAll static void closeClient() { if (client != null) client.shutdown(); }
    @BeforeEach void setUp() { store = new EsdbEventStore(client, "it-" + System.nanoTime() + "-"); }

    @Test void appendReadRangeAndGlobalReadFollowStrongContract() {
        TestId id = new TestId("order-1");
        store.append(ORDER_TYPE, id, Arrays.asList(new TestEvent(id), new TestEvent(id), new TestEvent(id)), 0);
        List<StoredEvent> all = store.read(ORDER_TYPE, id);
        assertThat(all).extracting(StoredEvent::version).containsExactly(1L, 2L, 3L);
        assertThat(store.read(ORDER_TYPE, id, 2, 3)).extracting(StoredEvent::version).containsExactly(2L, 3L);
        assertThat(store.readAll(0, 10)).allSatisfy(event -> assertThat(event.aggregateType()).isEqualTo(ORDER_TYPE));
    }static final class TestId implements AggregateRootId {
        private final String value;

        public TestId(String value) {
            this.value = value;
        }
        public String value() { return value; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestId)) return false;
            TestId other = (TestId) o;
            return Objects.equals(this.value, other.value);
        }
        @Override
        public int hashCode() {
            return java.util.Objects.hash(value);
        }
        @Override
        public String toString() {
            return "TestId{" + "value=" + value + "}";
        }
        private static final EntityType TYPE = new StringEntityType("Order");
        @Override public EntityType getType() { return TYPE; }
        @Override public String asString() { return value; }
        @Override public String asTypedString() { return TYPE.asString() + ":" + value; }
    
    }
    static final class TestEvent extends DomainEvent<TestId> {
        TestEvent() { super(); }
        TestEvent(TestId id) { super(new EntityIdPath(id)); }
    }
}
