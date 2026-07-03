package io.ddd4j.sample.cqrs.person.query;

import io.ddd4j.core.cqrs.readmodel.TypedEventHandler;
import io.ddd4j.sample.cqrs.person.domain.PersonDeletedEvent;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

/**
 * 人员删除事件处理器（读模型）。
 *
 * <p>当人员被删除时，从内存列表中移除对应条目，
 * 用于维护人员列表的读模型投影。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class PersonDeletedEventHandler implements TypedEventHandler<PersonDeletedEvent> {

    private final ConcurrentMap<String, PersonListEntry> entries;

    public PersonDeletedEventHandler(ConcurrentMap<String, PersonListEntry> entries) {
        this.entries = Objects.requireNonNull(entries, "entries must not be null");
    }

    @Override
    public String getEventType() {
        return PersonDeletedEvent.TYPE;
    }

    @Override
    public Class<PersonDeletedEvent> getEventClass() {
        return PersonDeletedEvent.class;
    }

    @Override
    public void handle(PersonDeletedEvent event) {
        entries.remove(event.getPersonId().getValue());
    }
}
