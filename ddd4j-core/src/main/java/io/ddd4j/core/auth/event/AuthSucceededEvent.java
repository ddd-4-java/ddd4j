package io.ddd4j.core.auth.event;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;

import java.time.Instant;

/**
 * 登录成功事件（通用鉴权事件）。
 *
 * <p>由具体 {@link io.ddd4j.core.subject.Subject} 实现在建立会话时发布。
 * 业务方可通过 {@link io.ddd4j.core.ddd.event.DomainEventPublisher} 订阅。
 *
 * <p>各框架适配层应负责把 ddd4j 通用事件桥接到本地事件总线：
 * <ul>
 *   <li>Spring：{@code SpringDomainEventPublisher} 解包 DomainEvent 后 publishEvent</li>
 *   <li>Quarkus：CDI {@code Event<LoginSucceededEvent>}</li>
 *   <li>Guice：Guava EventBus</li>
 *   <li>Javalin：业务方自定义</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public record AuthSucceededEvent(AuthRequest request, AuthPrincipal principal, String token, Instant occurredAt) {
}
