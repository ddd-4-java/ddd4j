package io.ddd4j.sample.cqrs.person.infrastructure;

import io.ddd4j.core.cqrs.readmodel.EventChunk;
import io.ddd4j.core.cqrs.readmodel.EventChunkReader;
import io.ddd4j.kit.lang.CollKit;
import io.ddd4j.sample.cqrs.person.domain.PersonEvent;
import io.ddd4j.sample.cqrs.person.domain.PersonId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 基于内存的事件存储实现。
 *
 * <p>用于演示 Event Sourcing 模式中的事件存储能力，
 * 支持追加事件、按人员 ID 查询和按序批量读取。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class InMemoryPersonEventStore implements EventChunkReader<PersonEvent> {

    /**
     * 事件流 ID
     */
    public static final String STREAM_ID = "person-stream";

    /**
     * 内存事件列表（线程安全）
     */
    private final List<PersonEvent> events = new CopyOnWriteArrayList<>();

    /**
     * 追加新事件到事件流。
     *
     * @param newEvents 新事件列表
     */
    public void append(List<PersonEvent> newEvents) {
        if (CollKit.isEmpty(newEvents)) {
            return;
        }
        events.addAll(newEvents);
    }

    /**
     * 根据人员 ID 查询关联的所有事件。
     *
     * @param personId 人员 ID
     * @return 关联的事件列表
     */
    public List<PersonEvent> readByPersonId(PersonId personId) {
        PersonId id = Objects.requireNonNull(personId, "personId must not be null");
        return events.stream()
                .filter(event -> Objects.equals(id, event.getPersonId()))
                .toList();
    }

    @Override
    public EventChunk<PersonEvent> read(String streamId, long fromEventNumber, int chunkSize, Collection<String> eventTypes) {
        if (fromEventNumber >= events.size()) {
            return EventChunk.empty(fromEventNumber);
        }
        int fromIndex = Math.toIntExact(fromEventNumber);
        int toIndex = Math.min(events.size(), fromIndex + chunkSize);
        List<PersonEvent> selectedEvents = new ArrayList<>();
        for (PersonEvent event : events.subList(fromIndex, toIndex)) {
            if (CollKit.isEmpty(eventTypes) || eventTypes.contains(event.getEventType())) {
                selectedEvents.add(event);
            }
        }
        return new EventChunk<>(selectedEvents, toIndex);
    }
}
