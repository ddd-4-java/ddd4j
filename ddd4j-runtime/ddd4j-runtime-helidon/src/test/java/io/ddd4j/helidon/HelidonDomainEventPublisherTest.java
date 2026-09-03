package io.ddd4j.helidon;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.StringEntityId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.enterprise.event.Event;
import javax.enterprise.inject.spi.BeanManager;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HelidonDomainEventPublisherTest {

    @Mock
    private BeanManager beanManager;

    @Mock
    private Event<Object> event;

    @Test
    void publishFiresEvent() {
        when(beanManager.getEvent()).thenReturn(event);
        HelidonDomainEventPublisher publisher = new HelidonDomainEventPublisher(beanManager);

        publisher.publish((Object) "payload");

        verify(event).fire("payload");
    }

    @Test
    void publishSkipsNullEvent() {
        HelidonDomainEventPublisher publisher = new HelidonDomainEventPublisher(beanManager);

        publisher.publish((Object) null);

        verify(event, never()).fire(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publishDomainEventFiresTypedEvent() {
        when(beanManager.getEvent()).thenReturn(event);
        HelidonDomainEventPublisher publisher = new HelidonDomainEventPublisher(beanManager);
        DomainEvent<EntityId> domainEvent = new DomainEvent<EntityId>(new EntityIdPath(new StringEntityId("orders"), new StringEntityId("created"))) {
        };

        publisher.publish(domainEvent);

        verify(event).fire(domainEvent);
    }

    @Test
    void constructorRejectsNullBeanManager() {
        assertThrows(NullPointerException.class, () -> new HelidonDomainEventPublisher(null));
    }
}
