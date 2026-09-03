package io.ddd4j.dropwizard;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.StringEntityId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DropwizardDomainEventPublisherTest {

    @Test
    void publishNotifiesAllListeners() {
        Consumer<Object> first = mock(Consumer.class);
        Consumer<Object> second = mock(Consumer.class);
        DropwizardDomainEventPublisher publisher =
                new DropwizardDomainEventPublisher(Arrays.asList(first, second));

        publisher.publish((Object) "payload");

        verify(first).accept("payload");
        verify(second).accept("payload");
    }

    @Test
    void publishSkipsNullEvent() {
        Consumer<Object> listener = mock(Consumer.class);
        DropwizardDomainEventPublisher publisher =
                new DropwizardDomainEventPublisher(Collections.singletonList(listener));

        publisher.publish((Object) null);

        org.mockito.Mockito.verifyNoInteractions(listener);
    }

    @Test
    void publishDomainEventNotifiesListeners() {
        Consumer<Object> listener = mock(Consumer.class);
        DropwizardDomainEventPublisher publisher =
                new DropwizardDomainEventPublisher(Collections.singletonList(listener));
        DomainEvent<EntityId> event = new DomainEvent<EntityId>(new EntityIdPath(new StringEntityId("orders"), new StringEntityId("created"))) {
        };

        publisher.publish(event);

        verify(listener).accept(event);
    }

    @Test
    void constructorRejectsNullAndSnapshotsListeners() {
        assertThrows(NullPointerException.class, () -> new DropwizardDomainEventPublisher(null));

        List<Consumer<Object>> mutable = new ArrayList<>();
        Consumer<Object> lateListener = mock(Consumer.class);
        DropwizardDomainEventPublisher publisher = new DropwizardDomainEventPublisher(mutable);
        mutable.add(lateListener);

        publisher.publish((Object) "payload");
        org.mockito.Mockito.verifyNoInteractions(lateListener);
    }
}
