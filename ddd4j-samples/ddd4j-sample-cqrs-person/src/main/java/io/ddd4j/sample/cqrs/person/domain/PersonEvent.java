package io.ddd4j.sample.cqrs.person.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.ddd4j.core.cqrs.readmodel.TypedEvent;

import java.time.Instant;

/**
 * 人员领域事件接口。
 *
 * <p>所有人员相关的事件（创建、删除等）均实现此接口，
 * 用于事件溯源和读模型投影。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface PersonEvent extends TypedEvent {

    @Override
    @JsonIgnore
    String getEventType();

    /**
     * 获取事件关联的人员 ID。
     *
     * @return 人员 ID
     */
    PersonId getPersonId();

    /**
     * 获取事件发生时间。
     *
     * @return 事件发生时间戳
     */
    Instant getOccurredAt();
}
