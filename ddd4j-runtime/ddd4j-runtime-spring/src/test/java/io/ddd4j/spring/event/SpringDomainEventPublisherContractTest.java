package io.ddd4j.spring.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过 <code>Proxy.newProxyInstance()</code>、<code>ApplicationEventPublisher.class.getClassLoader()</code>、<code>method.getName()</code> 验证 <code>SpringDomainEventPublisherContract</code> 的运行行为。
 */
class SpringDomainEventPublisherContractTest {

    /**
     * 验证<code>published.size()</code> 等于 <code>2</code>，<code>published.get(0)</code> 与…为同一实例 <code>event</code>，<code>published.get(1)</code> 与…为同一实例 <code>event</code>。
     */
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

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void requiresSpringPublisher() {
        assertThrows(NullPointerException.class, () -> new SpringDomainEventPublisher(null));
    }
}
