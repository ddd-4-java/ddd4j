/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.core.ddd.event;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.kit.lang.StrKit;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * 领域事件基类（纯 Java，零 Spring 依赖）。
 *
 * <p>融合了进程内事件和事件溯源（ES）两条轨道：
 * <ul>
 *   <li><b>进程内事件</b> —— 通过 {@link #publish()} 发布到 {@link DomainEventPublisher}，
 *       支持 {@link #tenantIn(String...)} 租户过滤和 {@link #supports(String...)} 策略过滤</li>
 *   <li><b>事件溯源（ES）</b> —— 使用 ddd4j 的纯 Java 元数据模型，
 *       包含 {@code eventId} / {@code entityIdPath} / {@code aggregateVersion} /
 *       {@code correlationId} / {@code causationId} 完整元数据，可序列化持久化到 EventStore</li>
 * </ul>
 *
 * <h3>框架适配层注入 publisher</h3>
 * <pre>{@code
 * Contexts.register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, publisher);
 * }</pre>
 *
 * <h3>业务方发布事件</h3>
 * <pre>{@code
 * new OrderCreatedEvent(orderId, amount).publish();
 * }</pre>
 *
 * @param <ID> 聚合根标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@Slf4j
@SuppressWarnings("unchecked")
public abstract class DomainEvent<ID extends EntityId> implements Event, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final ClassValue<EventType> EVENT_TYPES = new ClassValue<>() {
        @Override
        protected EventType computeValue(Class<?> type) {
            return new EventType(type.getSimpleName());
        }
    };

    /**
     * 默认主题（可通过系统属性 {@code ddd4j.mq.default-topic} 覆盖）。
     */
    @Setter
    @Getter
    private static volatile String defaultTopic = System.getProperty("ddd4j.mq.default-topic", "DEFAULT");

    /**
     * 事件支持的策略键集合（策略模式）。
     *
     * <p>监听器通过 {@link @EventListener#supports()} 声明它处理哪些策略键，
     * 消费时框架检查事件持有的 {@code supportKeys} 与监听器声明的键是否有交集。
     * 为 {@code null} 或空时不做策略过滤。
     */
    @Setter
    @Getter
    private Set<String> supportKeys;

    /**
     * 事件处理结果（由 publisher 写回）。
     */
    @Setter
    private Object result;

    @JsonProperty("event-id")
    private EventId eventId;

    @JsonProperty("event-timestamp")
    private ZonedDateTime eventTimestamp;

    @JsonProperty("correlation-id")
    private EventId correlationId;

    @JsonProperty("causation-id")
    private EventId causationId;

    @JsonProperty("entity-id-path")
    private EntityIdPath entityIdPath;

    @JsonProperty("aggregate-version")
    private AggregateVersion aggregateVersion;

    /**
     * 默认构造器（Jackson 反序列化 + 事件回放时使用，子类必须保留无参构造）。
     */
    protected DomainEvent() {
        this.eventId = new EventId();
        this.eventTimestamp = ZonedDateTime.now();
    }

    /**
     * 使用字符串实体标识构造领域事件。
     *
     * <p>适用于尚未定义专用实体标识值对象的轻量业务；复杂领域应优先传入
     * {@link EntityIdPath}，保留完整聚合路径语义。</p>
     *
     * @param entityId 非空字符串实体标识
     */
    protected DomainEvent(String entityId) {
        this(new EntityIdPath(new StringEntityId(entityId)));
    }

    /**
     * 构造领域事件。
     *
     * @param entityIdPath 从聚合根到事件源的路径
     */
    protected DomainEvent(EntityIdPath entityIdPath) {
        this();
        this.entityIdPath = Objects.requireNonNull(entityIdPath, "entityIdPath must not be null");
    }

    /**
     * 构造领域事件（带因果关联）。
     *
     * @param entityIdPath 从聚合根到事件源的路径
     * @param respondTo    导致本事件的前置事件
     */
    protected DomainEvent(EntityIdPath entityIdPath, Event respondTo) {
        this(entityIdPath);
        Event causingEvent = Objects.requireNonNull(respondTo, "respondTo must not be null");
        this.correlationId = Objects.nonNull(causingEvent.getCorrelationId())
                ? causingEvent.getCorrelationId() : causingEvent.getEventId();
        this.causationId = causingEvent.getEventId();
    }

    /**
     * 获取面向业务日志和兼容监听器的字符串事件源。
     *
     * @return 当前事件实体标识的字符串形式
     */
    public String source() {
        return getEntityId().asString();
    }

    /**
     * 默认以事件类简单名称作为稳定事件类型。
     *
     * <p>只读属性（{@code access = READ_ONLY}）：序列化仍输出 {@code event-type}；
     * 反序列化跳过绑定——值由 {@link ClassValue} 从 {@code getClass()} 派生，天然正确，
     * 亦不触发默认 {@code FAIL_ON_UNKNOWN_PROPERTIES} 的未知属性失败。
     *
     * @return 事件类型
     */
    @Override
    @JsonProperty(value = "event-type", access = JsonProperty.Access.READ_ONLY)
    public EventType getEventType() {
        return EVENT_TYPES.get(getClass());
    }

    /**
     * 返回事件标识。
     *
     * @return 事件标识
     */
    @Override
    @JsonIgnore
    public EventId getEventId() {
        return eventId;
    }

    /**
     * 返回事件创建时间。
     *
     * @return 创建时间
     */
    @Override
    @JsonIgnore
    public ZonedDateTime getEventTimestamp() {
        return eventTimestamp;
    }

    /**
     * 返回关联事件标识。
     *
     * @return 关联事件标识；没有时返回 {@code null}
     */
    @Override
    @JsonIgnore
    public EventId getCorrelationId() {
        return correlationId;
    }

    /**
     * 返回因果事件标识。
     *
     * @return 因果事件标识；没有时返回 {@code null}
     */
    @Override
    @JsonIgnore
    public EventId getCausationId() {
        return causationId;
    }

    /**
     * 返回事件源的完整实体标识路径。
     *
     * @return 实体标识路径
     */
    @JsonIgnore
    public EntityIdPath getEntityIdPath() {
        return entityIdPath;
    }

    /**
     * 返回事件源实体标识。
     *
     * @return 事件源实体标识
     */
    @JsonIgnore
    public ID getEntityId() {
        return entityIdPath.last();
    }

    /**
     * 返回聚合版本。
     *
     * @return 聚合版本；未设置时返回 {@code null}
     */
    @JsonIgnore
    public AggregateVersion getAggregateVersion() {
        return aggregateVersion;
    }

    /**
     * 返回聚合版本整数。
     *
     * @return 聚合版本；未设置时返回 {@code null}
     */
    @JsonIgnore
    public Long getAggregateVersionInteger() {
        return Objects.nonNull(aggregateVersion) ? aggregateVersion.asInt() : null;
    }

    /**
     * 为事件回放设置聚合版本。
     *
     * @param aggregateVersion 聚合版本
     */
    public void setAggregateVersion(AggregateVersion aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
    }

    /**
     * 获取事件处理结果。
     *
     * @param <R> 结果类型
     * @return 处理结果
     */
    public <R> R result() {
        return (R) this.result;
    }

    /**
     * 租户判断。
     *
     * <p>使用方式：监听方法标注
     * {@code @EventListener(condition = "#event.tenantIn('xxx', 'xxx')")}
     *
     * @param tenantIds 指定租户 ID 才能订阅
     * @return 该租户能否监听
     */
    public boolean tenantIn(String... tenantIds) {
        if (Objects.isNull(tenantIds)) {
            return Boolean.FALSE;
        }
        String tenantId = ThreadContext.get(ContextConstants.TENANT_ID);
        return Arrays.asList(tenantIds).contains(tenantId);
    }

    /**
     * 策略匹配：检查事件持有的 {@link #supportKeys} 是否包含给定的任意一个键。
     *
     * <p>使用方式：监听方法标注
     * {@code @EventListener(condition = "#event.supports('paid', 'shipped')")}
     *
     * @param keys 监听器声明的策略键
     * @return 事件支持任一键时 {@code true}；事件未设置策略或无交集时 {@code false}
     */
    public boolean supports(String... keys) {
        if (Objects.isNull(supportKeys) || supportKeys.isEmpty() || Objects.isNull(keys) || keys.length == 0) {
            return false;
        }
        for (String key : keys) {
            if (supportKeys.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 发布事件。
     *
     * <p>通过 {@link Contexts} 按「线程优先 → 全局兜底」策略查找 {@link DomainEventPublisher}。
     * 框架适配层应在启动期通过 {@link Contexts#register(String, Class, Object)} 注入 publisher。
     *
     * @param <R> 返回类型
     * @return 发布结果
     * @throws IllegalStateException 未找到 DomainEventPublisher
     */
    public <R> R publish() {
        DomainEventPublisher publisher = Contexts.getOrThrow(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class);
        publisher.publish(this);
        return (R) result;
    }

}
