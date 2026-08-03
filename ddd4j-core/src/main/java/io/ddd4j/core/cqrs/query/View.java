package io.ddd4j.core.cqrs.query;

import java.util.Collection;

/**
 * CQRS 读侧视图抽象（ddd4j 唯一推荐）。
 * <p>
 * 取代以下两个历史接口：
 * <ul>
 *   <li>原有的轻量查询标记接口</li>
 *   <li>原有的 JPA 查询抽象</li>
 *   <li>{@code io.ddd4j.core.cqrs.readmodel.ProjectionView}（ddd4j 框架无关投影）</li>
 * </ul>
 *
 * <h3>统一抽象</h3>
 * <p>
 * 本接口同时承担：
 * <ul>
 *   <li><b>框架无关投影单元</b>（继承自 ProjectionView）</li>
 *   <li><b>JPA 实体</b>（与 {@code @Entity} 共存，由业务方决定持久化方式）</li>
 * </ul>
 *
 * <h3>与 {@code ProjectionView} 的关系</h3>
 * <p>
 * 本接口是 {@link io.ddd4j.core.cqrs.readmodel.ProjectionView} 的业务层别名，
 * 提供一致的 DDD 战术命名（{@code View} vs 技术命名 {@code ProjectionView}）。
 * 框架无关投影使用 {@code ProjectionView}，业务侧视图使用本接口。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * &#64;Entity
 * &#64;Table(name = "order_list_view")
 * public class OrderListView implements View {
 *     &#64;Id private String orderId;
 *     private String status;
 *     private Money total;
 *
 *     // getters / setters
 *
 *     // 事件处理器（Spring/Quarkus 自动注册）
 *     &#64;EventHandler
 *     public void on(OrderCreatedEvent event) {
 *         this.orderId = event.getOrderId().asString();
 *         this.status = "PENDING";
 *         this.total = event.getTotal();
 *     }
 * }
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public interface View {

    /**
     * 视图名称。
     */
    String getName();

    /**
     * 视图订阅的事件类型集合。
     *
     * @return 事件类型集合
     */
    Collection<String> getEventTypes();
}
