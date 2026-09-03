package io.ddd4j.data.event.store.esdb;

import com.eventstore.dbclient.AppendToStreamOptions;
import com.eventstore.dbclient.EventData;
import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.ExpectedRevision;
import com.eventstore.dbclient.StreamNotFoundException;
import com.eventstore.dbclient.WrongExpectedVersionException;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** ESDB 强类型 EventStore 的单元契约。 */
@ExtendWith(MockitoExtension.class)
class EsdbEventStoreTest {
    private static final String ORDER_TYPE = "Order";
    @Mock EventStoreDBClient client;
    @Captor ArgumentCaptor<String> streamCaptor;

    @Test
    void expectedRevisionShouldFollowCoreCurrentVersionContract() {
        assertThat(EsdbEventStore.toExpectedRevision(0)).isEqualTo(ExpectedRevision.noStream());
        assertThat(EsdbEventStore.toExpectedRevision(3)).isEqualTo(ExpectedRevision.expectedRevision(2));
    }


    private static <T> java.util.concurrent.CompletableFuture<T> failedFuture(Throwable ex) {
        java.util.concurrent.CompletableFuture<T> f = new java.util.concurrent.CompletableFuture<>();
        f.completeExceptionally(ex);
        return f;
    }
    @Test
    void appendShouldUseAggregateTypeInStreamAndPreserveCoreEventIdentity() {
        TestId id = new TestId("order-1");
        when(client.appendToStream(anyString(), any(AppendToStreamOptions.class), any(EventData[].class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        new EsdbEventStore(client, "test-").append(ORDER_TYPE, id, java.util.Arrays.asList(new TestEvent(id)), 0);

        verify(client).appendToStream(streamCaptor.capture(), any(AppendToStreamOptions.class), any(EventData[].class));
        assertThat(streamCaptor.getValue()).isEqualTo("test-Order::order-1");
    }

    @Test
    void appendEmptyEventsMustNotCallClient() {
        new EsdbEventStore(client).append(ORDER_TYPE, new TestId("empty"), java.util.Arrays.asList(), 0);
        verifyNoInteractions(client);
    }

    @Test
    void versionConflictMustUseCoreException() {
        WrongExpectedVersionException conflict = org.mockito.Mockito.mock(WrongExpectedVersionException.class);
        when(conflict.getActualVersion()).thenReturn(ExpectedRevision.expectedRevision(0));
        when(client.appendToStream(anyString(), any(AppendToStreamOptions.class), any(EventData[].class)))
                .thenReturn(failedFuture(conflict));

        assertThatThrownBy(() -> new EsdbEventStore(client).append(ORDER_TYPE, new TestId("conflict"),
                java.util.Arrays.asList(new TestEvent(new TestId("conflict"))), 0))
                .isInstanceOf(AggregateVersionConflictException.class);
    }

    @Test
    void absentStronglyTypedStreamMustReadEmpty() {
        StreamNotFoundException absent = org.mockito.Mockito.mock(StreamNotFoundException.class);
        when(client.readStream(anyString(), any())).thenReturn(failedFuture(absent));
        assertThat(new EsdbEventStore(client).read(ORDER_TYPE, new TestId("missing"))).isEmpty();
    }

    static final class TestId implements AggregateRootId {
        private static final EntityType TYPE = new StringEntityType("Order");
        private final String value;
        TestId(String value) { this.value = value; }
        @Override public EntityType getType() { return TYPE; }
        @Override public String asString() { return value; }
        @Override public String asTypedString() { return TYPE.asString() + ":" + value; }
        @Override public boolean equals(Object o) { return this == o || (o instanceof TestId && java.util.Objects.equals(value, ((TestId)o).value)); }
        @Override public int hashCode() { return java.util.Objects.hashCode(value); }
        @Override public String toString() { return "TestId{" + value + "}"; }
    }

    static final class TestEvent extends DomainEvent<TestId> {
        TestEvent() { super(); }
        TestEvent(TestId id) { super(new EntityIdPath(id)); }
    }
}
