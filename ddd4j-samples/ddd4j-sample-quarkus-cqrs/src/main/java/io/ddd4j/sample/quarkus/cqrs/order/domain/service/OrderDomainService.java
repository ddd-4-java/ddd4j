package io.ddd4j.sample.quarkus.cqrs.order.domain.service;

import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.sample.quarkus.cqrs.order.domain.model.Order;
import io.ddd4j.sample.quarkus.cqrs.order.domain.model.OrderStatus;
import io.ddd4j.sample.quarkus.cqrs.order.domain.repository.OrderRepository;
import io.ddd4j.sample.quarkus.cqrs.order.domain.event.OrderCancelledEvent;
import io.ddd4j.sample.quarkus.cqrs.order.domain.event.OrderPaidEvent;
import io.ddd4j.sample.quarkus.cqrs.order.infrastructure.InMemoryOrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Objects;
import java.util.Optional;

/**
 * 订单领域服务：跨聚合根的业务规则（如批量取消、统计）。
 *
 * <p>本服务演示在 Quarkus CDI 下通过 {@code Contexts.getOrThrow} 查找
 * ddd4j 启动期注入的 SPI：
 * <ul>
 *   <li>{@link DomainEventPublisher}：转发跨聚合根的领域事件</li>
 *   <li>{@link I18nProvider}：业务异常文案国际化</li>
 *   <li>{@link SubjectProvider}：审计日志记录当前操作者</li>
 * </ul>
 *
 * <p>实际业务中，领域服务应避免直接依赖 SPI；而是通过基础设施层适配。
 * 这里刻意展示 SPI 的可见性，便于学习 ddd4j 在 Quarkus 中的注入能力。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class OrderDomainService {

    private final OrderRepository repository;

    @Inject
    DomainEventPublisher domainEventPublisher;

    @Inject
    I18nProvider i18nProvider;

    @Inject
    SubjectProvider subjectProvider;

    @Inject
    public OrderDomainService(OrderRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * 批量取消指定买家的所有草稿订单。
     *
     * <p>本示例中 {@link OrderRepository} 接口未定义 {@code findAll}，
     * 故直接注入 {@link InMemoryOrderRepository}；真实项目可让
     * {@code OrderRepository} 继承 {@code RichRepository} 以获得充血查询能力。
     *
     * @param buyerId 买家 ID
     * @return 被取消的订单数量
     */
    public int cancelAllDraftsOf(String buyerId) {
        if (buyerId == null || buyerId.isBlank()) {
            throw new IllegalArgumentException(i18nProvider.getMessage("order.error.buyerIdRequired"));
        }
        // 演示：在 Quarkus 中如何同时获取领域接口与具体实现
        if (!(repository instanceof InMemoryOrderRepository inMemory)) {
            throw new IllegalStateException("cancelAllDraftsOf requires InMemoryOrderRepository in this sample");
        }
        int cancelled = 0;
        for (Order order : inMemory.findAll()) {
            if (Objects.equals(buyerId, order.buyerId()) && OrderStatus.DRAFT == order.status()) {
                order.cancel();
                repository.save(order);
                // 显式发布事件，演示 SPI 在领域服务中的用法
                domainEventPublisher.publish(new OrderCancelledEvent(order.id()));
                cancelled++;
            }
        }
        return cancelled;
    }

    /**
     * 演示：基于 SubjectProvider 审计当前用户。
     *
     * @param orderId 订单 ID
     * @return 操作人描述
     */
    public Optional<String> auditOperator(String orderId) {
        return repository.findById(orderId)
                .map(order -> {
                    String subject = Optional.ofNullable(subjectProvider.getSubject())
                            .map(s -> s.toString())
                            .orElse("anonymous");
                    return "order=" + order.id() + ", operator=" + subject;
                });
    }

    /**
     * 演示：业务异常中通过 i18n 拼接文案。
     *
     * @param orderId 订单 ID
     * @return 订单或文案
     */
    public Order mustFindOrI18nError(String orderId) {
        return repository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        i18nProvider.getMessage("order.error.notFound", orderId)));
    }

    /**
     * 演示：直接通过 OrderPaidEvent 触发一次跨聚合通知（不影响 Order 聚合内部事件）。
     *
     * @param orderId 订单 ID
     */
    public void notifyPaid(String orderId) {
        domainEventPublisher.publish(new OrderPaidEvent(orderId));
    }
}