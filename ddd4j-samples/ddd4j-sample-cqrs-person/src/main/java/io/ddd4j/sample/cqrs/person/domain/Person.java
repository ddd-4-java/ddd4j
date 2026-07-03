package io.ddd4j.sample.cqrs.person.domain;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 人员聚合根（Event Sourcing 风格）。
 *
 * <p>采用事件溯源模式，所有状态变更通过领域事件来表达。
 * 每次变更产生的事件被追加到 {@link #changes} 列表中，由仓库持久化。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
public class Person {

    /**
     * 未提交的领域事件列表
     */
    private final List<PersonEvent> changes = new ArrayList<>();
    /**
     * 人员 ID
     */
    private PersonId id;
    /**
     * 人员姓名
     */
    private PersonName name;
    /**
     * 是否已删除
     */
    private boolean deleted;

    /**
     * 创建新人员。
     *
     * @param id   人员 ID
     * @param name 人员姓名
     * @return 新创建的人员实例
     */
    public static Person create(PersonId id, PersonName name) {
        Person person = new Person();
        person.apply(new PersonCreatedEvent(id, name));
        return person;
    }

    /**
     * 删除人员（标记删除状态）。
     */
    public void delete() {
        if (!deleted) {
            apply(new PersonDeletedEvent(id));
        }
    }

    /**
     * 从历史事件列表中恢复人员状态。
     *
     * @param events 历史事件列表
     */
    public void loadFromHistory(List<PersonEvent> events) {
        for (PersonEvent event : events) {
            mutate(event);
        }
    }

    /**
     * 获取并清空未提交的领域事件列表。
     *
     * @return 未提交的事件列表（不可变副本）
     */
    public List<PersonEvent> pullChanges() {
        List<PersonEvent> events = List.copyOf(changes);
        changes.clear();
        return events;
    }

    private void apply(PersonEvent event) {
        PersonEvent personEvent = Objects.requireNonNull(event, "event must not be null");
        mutate(personEvent);
        changes.add(personEvent);
    }

    private void mutate(PersonEvent event) {
        if (event instanceof PersonCreatedEvent createdEvent) {
            id = createdEvent.getPersonId();
            name = createdEvent.getName();
            deleted = false;
        }
        if (event instanceof PersonDeletedEvent) {
            deleted = true;
        }
    }
}
