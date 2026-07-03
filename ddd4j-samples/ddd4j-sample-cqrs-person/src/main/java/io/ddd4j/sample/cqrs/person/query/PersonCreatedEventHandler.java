package io.ddd4j.sample.cqrs.person.query;

import io.ddd4j.core.cqrs.readmodel.TypedEventHandler;
import io.ddd4j.sample.cqrs.person.domain.PersonCreatedEvent;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

/**
 * 人员创建事件处理器（读模型）。
 *
 * <p>当人员被创建时，将人员信息添加到内存列表中，
 * 用于维护人员列表的读模型投影。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class PersonCreatedEventHandler implements TypedEventHandler<PersonCreatedEvent> {

    private final ConcurrentMap<String, PersonListEntry> entries;

    public PersonCreatedEventHandler(ConcurrentMap<String, PersonListEntry> entries) {
        this.entries = Objects.requireNonNull(entries, "entries must not be null");
    }

    @Override
    public String getEventType() {
        return PersonCreatedEvent.TYPE;
    }

    @Override
    public Class<PersonCreatedEvent> getEventClass() {
        return PersonCreatedEvent.class;
    }

    @Override
    public void handle(PersonCreatedEvent event) {
        entries.put(
                event.getPersonId().getValue(),
                new PersonListEntry(event.getPersonId().getValue(), event.getName().getValue())
        );
    }
}
