package io.ddd4j.sample.cqrs.person.infrastructure;

import io.ddd4j.sample.cqrs.person.domain.Person;
import io.ddd4j.sample.cqrs.person.domain.PersonEvent;
import io.ddd4j.sample.cqrs.person.domain.PersonId;
import io.ddd4j.sample.cqrs.person.domain.PersonRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于内存的人员仓库实现。
 *
 * <p>使用 {@link InMemoryPersonEventStore} 实现事件溯源持久化，
 * 保存时持久化事件，查询时通过事件重放重建聚合状态。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class InMemoryPersonRepository implements PersonRepository {

    private final InMemoryPersonEventStore eventStore;

    /**
     * 构造函数。
     *
     * @param eventStore 事件存储（不可为 null）
     */
    public InMemoryPersonRepository(InMemoryPersonEventStore eventStore) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
    }

    @Override
    public void save(Person person) {
        eventStore.append(person.pullChanges());
    }

    @Override
    public Optional<Person> findById(PersonId id) {
        List<PersonEvent> events = eventStore.readByPersonId(id);
        if (events.isEmpty()) {
            return Optional.empty();
        }
        Person person = new Person();
        person.loadFromHistory(events);
        return Optional.of(person);
    }
}
