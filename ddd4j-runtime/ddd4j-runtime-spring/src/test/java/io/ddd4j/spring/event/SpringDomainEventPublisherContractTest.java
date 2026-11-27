package io.ddd4j.spring.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** \u901a\u8fc7 <code>Proxy.newProxyInstance()</code>\u3001<code>ApplicationEventPublisher.class.getClassLoader()</code>\u3001<code>method.getName()</code> \u9a8c\u8bc1 <code>SpringDomainEventPublisherContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class SpringDomainEventPublisherContractTest {

    /** \u9a8c\u8bc1<code>published.size()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>published.get(0)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>event</code>\uff0c<code>published.get(1)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>event</code>\u3002 */
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
        DomainEvent<StringEntityId> event = new DomainEvent<StringEntityId>("order-1") {
        };

        publisher.publish(event);
        publisher.publish((Object) event);
        publisher.publish((Object) "plain");
        publisher.publish((Object) null);
        publisher.publish((DomainEvent<StringEntityId>) null);

        assertEquals(2, published.size());
        assertSame(event, published.get(0));
        assertSame(event, published.get(1));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void requiresSpringPublisher() {
        assertThrows(NullPointerException.class, () -> new SpringDomainEventPublisher(null));
    }
}
