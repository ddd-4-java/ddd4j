package io.ddd4j.data.eventstore.jpa;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventId;
import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JpaEventStore} 单元测试（纯 Mockito，无 Spring 上下文——容器内行为归 Task 4.4 IT）。
 *
 * <p>覆盖四条关键路径：乐观锁冲突短路、顺序追加的版本递增与实体组装、
 * 实体→{@link StoredEvent} 重建（含 StringAggregateRootId 适配与空安全 correlationId）、
 * 未知 eventType 的异常翻译。payload 序列化 mock 掉（Jackson 往返已由
 * EventPayloadSerializer 自身测试覆盖）。
 */
@DisplayName("JpaEventStore")
@ExtendWith(MockitoExtension.class)
class JpaEventStoreTest {

    private static final String AGGREGATE_TYPE = "SampleAggregate";

    private static final String AGGREGATE_ID = "agg-1";

    @Mock
    private SpringDataStoredEventRepository repository;

    @Mock
    private EventPayloadSerializer serializer;

    private JpaEventStore eventStore;

    @BeforeEach
    void setUp() {
        eventStore = new JpaEventStore(repository, serializer);
    }

    @Test
    void append_版本冲突_应抛AggregateVersionConflictException且不调用save() {
        when(repository.findCurrentVersion(AGGREGATE_TYPE, AGGREGATE_ID)).thenReturn(3L);
        SampleEvent event = new SampleEvent();

        AggregateVersionConflictException exception = catchThrowableOfType(
                () -> eventStore.append(AGGREGATE_TYPE, new TestAggregateId(AGGREGATE_ID), List.of(event), 0L),
                AggregateVersionConflictException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.aggregateType()).isEqualTo(AGGREGATE_TYPE);
        assertThat(exception.aggregateId()).isEqualTo(AGGREGATE_ID);
        assertThat(exception.expectedVersion()).isZero();
        assertThat(exception.actualVersion()).isEqualTo(3L);
        verify(repository, never()).save(any());
    }

    @Test
    void append_顺序追加_应逐事件递增版本并组装实体() {
        when(repository.findCurrentVersion(AGGREGATE_TYPE, AGGREGATE_ID)).thenReturn(0L);
        SampleEvent first = new SampleEvent();
        SampleEvent second = new SampleEvent();
        // third 经 respondTo 因果构造器派生自 first：correlationId/causationId 非空分支的组装断言
        SampleEvent third = new SampleEvent(first);
        when(serializer.serialize(any(DomainEvent.class))).thenReturn("{}");

        eventStore.append(AGGREGATE_TYPE, new TestAggregateId(AGGREGATE_ID),
                List.of(first, second, third), 0L);

        ArgumentCaptor<StoredEventEntity> captor = ArgumentCaptor.forClass(StoredEventEntity.class);
        verify(repository, times(3)).save(captor.capture());
        List<StoredEventEntity> saved = captor.getAllValues();

        assertThat(saved).extracting(StoredEventEntity::getVersion).containsExactly(1L, 2L, 3L);
        assertThat(saved).extracting(StoredEventEntity::getEventId).containsExactly(
                first.getEventId().asString(), second.getEventId().asString(), third.getEventId().asString());
        assertThat(saved).extracting(StoredEventEntity::getEventType)
                .containsOnly(SampleEvent.class.getName());
        assertThat(saved).extracting(StoredEventEntity::getPayload).containsOnly("{}");
        assertThat(saved).extracting(StoredEventEntity::getAggregateType).containsOnly(AGGREGATE_TYPE);
        assertThat(saved).extracting(StoredEventEntity::getAggregateId).containsOnly(AGGREGATE_ID);
        // 因果链：first 无因果（null）；third 的 correlationId=causationId=first.eventId
        //（first 自身无 correlationId，respondTo 语义取 cause 的 eventId 补位）
        assertThat(saved).extracting(StoredEventEntity::getCorrelationId)
                .containsExactly(null, null, first.getEventId().asString());
        assertThat(saved).extracting(StoredEventEntity::getCausationId)
                .containsExactly(null, null, first.getEventId().asString());
        assertThat(saved).extracting(StoredEventEntity::getCreatedAt).allSatisfy(
                createdAt -> assertThat(createdAt).isNotNull());
    }

    @Test
    void read_应重建StoredEvent且correlationId空安全() throws Exception {
        StoredEventEntity entity = new StoredEventEntity();
        entity.setEventId("11111111-1111-1111-1111-111111111111");
        entity.setAggregateType(AGGREGATE_TYPE);
        entity.setAggregateId(AGGREGATE_ID);
        entity.setVersion(1L);
        entity.setEventType(SampleEvent.class.getName());
        entity.setPayload("{}");
        entity.setCausationId("22222222-2222-2222-2222-222222222222");
        ZonedDateTime createdAt = ZonedDateTime.now();
        entity.setCreatedAt(createdAt);
        // position 由数据库生成、实体无 setter：经反射注入真值，断言读回侧按真实
        // position 重建（read 路径对 null position fail-loud，不静默归零）
        Field positionField = StoredEventEntity.class.getDeclaredField("position");
        positionField.setAccessible(true);
        positionField.set(entity, 42L);
        when(repository.findByAggregateTypeAndAggregateIdOrderByVersionAsc(AGGREGATE_TYPE, AGGREGATE_ID))
                .thenReturn(List.of(entity));
        SampleEvent event = new SampleEvent();
        // deserialize 返回 DomainEvent<?>（通配符），when().thenReturn() 会触发 javac 捕获转换
        // 不变性问题，改用 doReturn 绕开泛型捕获
        doReturn(event).when(serializer).deserialize(eq("{}"), eq(SampleEvent.class));

        List<StoredEvent> stored = eventStore.read(AGGREGATE_TYPE, new TestAggregateId(AGGREGATE_ID));

        assertThat(stored).hasSize(1);
        StoredEvent single = stored.get(0);
        assertThat(single.eventId()).isEqualTo(EventId.valueOf("11111111-1111-1111-1111-111111111111"));
        assertThat(single.aggregateType()).isEqualTo(AGGREGATE_TYPE);
        assertThat(single.aggregateId().asString()).isEqualTo(AGGREGATE_ID);
        assertThat(single.aggregateId().getType().asString()).isEqualTo("String");
        assertThat(single.version()).isEqualTo(1L);
        assertThat(single.position()).isEqualTo(42L);
        assertThat(single.timestamp()).isEqualTo(createdAt);
        assertThat(single.payload()).isSameAs(event);
        assertThat(single.correlationId()).isNull();
        assertThat(single.causationId()).isEqualTo(EventId.valueOf("22222222-2222-2222-2222-222222222222"));
    }

    @Test
    void read_未知eventType_应抛IllegalStateException且cause为ClassNotFoundException() {
        StoredEventEntity entity = new StoredEventEntity();
        entity.setEventId(UUID.randomUUID().toString());
        entity.setAggregateType(AGGREGATE_TYPE);
        entity.setAggregateId(AGGREGATE_ID);
        entity.setVersion(1L);
        entity.setEventType("no.such.Clazz");
        entity.setPayload("{}");
        entity.setCreatedAt(ZonedDateTime.now());
        when(repository.findByAggregateTypeAndAggregateIdOrderByVersionAsc(AGGREGATE_TYPE, AGGREGATE_ID))
                .thenReturn(List.of(entity));

        IllegalStateException exception = catchThrowableOfType(
                () -> eventStore.read(AGGREGATE_TYPE, new TestAggregateId(AGGREGATE_ID)),
                IllegalStateException.class);

        assertThat(exception).isNotNull();
        assertThat(exception).hasMessageContaining("no.such.Clazz");
        assertThat(exception).hasCauseInstanceOf(ClassNotFoundException.class);
    }

    /**
     * 测试样本事件：仅作 payload 载体（聚合定位由 append/read 参数提供）。
     */
    static final class SampleEvent extends DomainEvent<TestAggregateId> {

        SampleEvent() {
            super(AGGREGATE_ID);
        }

        SampleEvent(io.ddd4j.core.ddd.event.Event respondTo) {
            super(new EntityIdPath(new StringEntityId(AGGREGATE_ID)), respondTo);
        }
    }

    /**
     * 测试自有字符串聚合根标识（受测类内部的 StringAggregateRootId 为 private，无法引用；
     * 两者契约一致——AggregateRootId 接口三方法）。
     */
    private record TestAggregateId(String value) implements AggregateRootId {

        private static final StringEntityType TYPE = new StringEntityType("TestAggregate");

        @Override
        public EntityType getType() {
            return TYPE;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }
    }
}
