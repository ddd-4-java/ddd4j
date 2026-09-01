package io.ddd4j.core.cqrs.eventstore;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** 异步事件溯源存储的强类型同步契约（Reactor）。 */
public interface AsyncEventStore {
    Mono<Void> append(String aggregateType, AggregateRootId aggregateId,
                      List<? extends DomainEvent<?>> events, long expectedVersion);
    Flux<StoredEvent> read(String aggregateType, AggregateRootId aggregateId);
    Flux<StoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                           long fromVersion, long toVersion);
    Flux<StoredEvent> readAll(long fromPosition, int limit);
}
