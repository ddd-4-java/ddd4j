package io.ddd4j.spring.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpringDomainEventPublisherContractTest {

    @Test
    void publishesDomainEventsThroughBothOverloadsAndIgnoresOtherObjects() {
        List<Object> published = new ArrayList<>();
        ApplicationEventPublisher delegate = (ApplicationEventPublisher) Proxy.newProxyInstance(
                ApplicationEventPublisher.class.getClassLoader(),
                new Class<?>[]{ApplicationEventPublisher.class},
                (proxy, method, args) -> {
                    if ("publishEvent".equals(method.getName())) {
                        published.add(args[0]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        SpringDomainEventPublisher publisher = new SpringDomainEventPublisher(delegate);
        DomainEvent<StringEntityId> event = new DomainEvent<StringEntityId>("order-1") { };

        publisher.publish(event);
        publisher.publish((Object) event);
        publisher.publish((Object) "plain");
        publisher.publish((Object) null);
        publisher.publish((DomainEvent<StringEntityId>) null);

        assertEquals(2, published.size());
        assertSame(event, published.get(0));
        assertSame(event, published.get(1));
    }

    @Test
    void requiresSpringPublisher() {
        assertThrows(NullPointerException.class, () -> new SpringDomainEventPublisher(null));
    }
}
