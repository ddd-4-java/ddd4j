package io.ddd4j.runtime.testkit;

import io.ddd4j.cache.subject.InMemorySubject;
import io.ddd4j.cache.subject.InMemorySubjectProvider;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import org.fuin.ddd4j.core.EntityId;

import java.util.List;
import java.util.Map;

/**
 * Runtime 契约测试使用的框架无关服务夹具。
 */
public final class RuntimeFixtures {

    private final DomainEventPublisher publisher = new DomainEventPublisher() {
        @Override
        public <ID extends EntityId> void publish(DomainEvent<ID> event) {
            // Contract tests only verify registration and lifecycle.
        }
    };
    private final SubjectProvider subjectProvider = new InMemorySubjectProvider(new InMemorySubject(event -> {
    }));
    private final I18nProvider i18nProvider = I18nProvider.DEFAULT;
    private final CommandBus commandBus = new DefaultCommandBus(List.of());

    public DomainEventPublisher publisher() {
        return publisher;
    }

    public SubjectProvider subjectProvider() {
        return subjectProvider;
    }

    public I18nProvider i18nProvider() {
        return i18nProvider;
    }

    public CommandBus commandBus() {
        return commandBus;
    }

    public Map<String, Class<?>> services() {
        return Map.of(
                SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class,
                SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class,
                SpiKeys.I18N_PROVIDER, I18nProvider.class,
                SpiKeys.COMMAND_BUS, CommandBus.class);
    }
}
