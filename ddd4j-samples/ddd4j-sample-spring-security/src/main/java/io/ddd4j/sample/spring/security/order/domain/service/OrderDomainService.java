package io.ddd4j.sample.spring.security.order.domain.service;

import io.ddd4j.sample.spring.security.order.domain.model.Money;
import io.ddd4j.sample.spring.security.order.domain.model.Order;
import io.ddd4j.sample.spring.security.order.domain.repository.OrderRepository;
import io.ddd4j.spring.annotation.DomainService;

import java.util.Objects;

/**
 * 订单领域服务。
 *
 * <p>领域服务（Domain Service）用于表达不属于单个聚合的领域逻辑。
 * 本例演示基于订单金额的 VIP 折扣计算：跨聚合的策略计算。
 *
 * <p>使用 {@link DomainService} 标注：ddd4j 自动融合 Spring {@code @Service} 元注解，
 * 自动被 Spring 容器扫描注册为 Service Bean。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@DomainService
public class OrderDomainService {

    private final OrderRepository orderRepository;

    /**
     * 构造函数。
     *
     * @param orderRepository 订单仓储
     */
    public OrderDomainService(OrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
    }

    /**
     * 根据买家历史订单数与订单金额计算折扣。
     *
     * <p>规则：
     * <ul>
     *   <li>订单总额 ≥ 1000 元：9 折</li>
     *   <li>订单总额 ≥ 500 元：95 折</li>
     *   <li>历史订单数 ≥ 10：额外 98 折</li>
     * </ul>
     *
     * @param buyerId 买家 ID
     * @param total   待折扣的总金额
     * @return 折后金额
     */
    public Money calculateDiscount(String buyerId, Money total) {
        Objects.requireNonNull(buyerId, "buyerId must not be null");
        Objects.requireNonNull(total, "total must not be null");
        Money discounted = total;
        if (total.amount().compareTo(new java.math.BigDecimal("1000")) >= 0) {
            discounted = discounted.discount(10);
        } else if (total.amount().compareTo(new java.math.BigDecimal("500")) >= 0) {
            discounted = discounted.discount(5);
        }
        long historicalCount = orderRepository.findById(buyerId).isPresent() ? 1 : 0;
        if (historicalCount >= 10) {
            discounted = discounted.discount(2);
        }
        return discounted;
    }

    /**
     * 预览订单折扣。
     *
     * @param order 订单聚合
     * @return 折后金额
     */
    public Money previewDiscount(Order order) {
        Objects.requireNonNull(order, "order must not be null");
        return calculateDiscount(order.buyerId(), order.totalAmount());
    }
}
