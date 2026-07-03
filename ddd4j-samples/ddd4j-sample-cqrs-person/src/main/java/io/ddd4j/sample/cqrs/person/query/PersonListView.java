package io.ddd4j.sample.cqrs.person.query;

import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.TypedEventDispatcher;
import io.ddd4j.sample.cqrs.person.domain.PersonCreatedEvent;
import io.ddd4j.sample.cqrs.person.domain.PersonDeletedEvent;
import io.ddd4j.sample.cqrs.person.domain.PersonEvent;
import io.ddd4j.sample.cqrs.person.infrastructure.InMemoryPersonEventStore;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 人员列表视图（读模型投影）。
 *
 * <p>通过事件回溯构建内存中的人员列表快照，
 * 提供按 ID 查询和全量查询能力。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class PersonListView implements ProjectionView<PersonEvent> {

    /**
     * 内存中的人员列表条目映射（线程安全）
     */
    private final ConcurrentMap<String, PersonListEntry> entries = new ConcurrentHashMap<>();

    /**
     * 事件分发器，按事件类型分发到对应的事件处理器
     */
    private final TypedEventDispatcher dispatcher = new TypedEventDispatcher(List.of(
            new PersonCreatedEventHandler(entries),
            new PersonDeletedEventHandler(entries)
    ));

    @Override
    public String getName() {
        return "person-list-view";
    }

    @Override
    public String getStreamId() {
        return InMemoryPersonEventStore.STREAM_ID;
    }

    @Override
    public String getCron() {
        return "0/5 * * * * ?";
    }

    @Override
    public Collection<String> getEventTypes() {
        return List.of(PersonCreatedEvent.TYPE, PersonDeletedEvent.TYPE);
    }

    @Override
    public void handleEvents(Collection<PersonEvent> events) {
        for (PersonEvent event : events) {
            dispatcher.dispatch(event);
        }
    }

    /**
     * 根据人员 ID 查询人员列表条目。
     *
     * @param personId 人员 ID
     * @return 查询结果
     */
    public Optional<PersonListEntry> findById(String personId) {
        return Optional.ofNullable(entries.get(personId));
    }

    /**
     * 查询所有人员列表条目。
     *
     * @return 所有条目（不可变列表）
     */
    public List<PersonListEntry> findAll() {
        return List.copyOf(entries.values());
    }

    /**
     * 获取当前快照（不可变 Map）。
     *
     * @return 人员列表快照
     */
    public Map<String, PersonListEntry> snapshot() {
        return Map.copyOf(entries);
    }
}
