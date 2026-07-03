package io.ddd4j.sample.cqrs.person.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;

/**
 * 人员删除事件。
 *
 * <p>当人员被删除时触发，包含人员标识信息。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
@ToString
@NoArgsConstructor
public class PersonDeletedEvent implements PersonEvent {

    /**
     * 事件类型标识
     */
    public static final String TYPE = "person.deleted";

    /**
     * 人员 ID
     */
    private PersonId personId;

    /**
     * 事件发生时间
     */
    private Instant occurredAt;

    public PersonDeletedEvent(PersonId personId) {
        this.personId = Objects.requireNonNull(personId, "personId must not be null");
        this.occurredAt = Instant.now();
    }

    @Override
    public String getEventType() {
        return TYPE;
    }
}
