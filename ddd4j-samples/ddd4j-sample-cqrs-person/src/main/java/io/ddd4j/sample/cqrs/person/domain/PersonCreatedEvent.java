package io.ddd4j.sample.cqrs.person.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;

/**
 * 人员创建事件。
 *
 * <p>当新人员被创建时触发，包含人员的基本信息。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
@ToString
@NoArgsConstructor
public class PersonCreatedEvent implements PersonEvent {

    /**
     * 事件类型标识
     */
    public static final String TYPE = "person.created";

    /**
     * 人员 ID
     */
    private PersonId personId;

    /**
     * 人员姓名
     */
    private PersonName name;

    /**
     * 事件发生时间
     */
    private Instant occurredAt;

    public PersonCreatedEvent(PersonId personId, PersonName name) {
        this.personId = Objects.requireNonNull(personId, "personId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.occurredAt = Instant.now();
    }

    @Override
    public String getEventType() {
        return TYPE;
    }
}
