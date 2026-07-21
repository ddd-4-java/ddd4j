package io.ddd4j.sample.spring.cqrs;

import io.ddd4j.spring.context.SpringContextBridge;
import io.ddd4j.spring.event.SpringDomainEventPublisher;
import io.ddd4j.web.webmvc.core.GlobalRestExceptionAdvice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * ddd4j + Spring Boot Order/Goods CQRS 示例启动类。
 *
 * <p>本示例在统一的 ddd4j 业务框架下演示 <b>CQRS 命令/查询分离</b> 与 <b>双业务（Order + Goods）</b> 编排：
 * <ul>
 *   <li><b>Order 业务（第二轨 / 充血模型）</b>：
 *     <ul>
 *       <li>{@code Order} 聚合根、{@code OrderLine}、{@code Money}、{@code OrderStatus}</li>
 *       <li>5 个领域事件：{@code OrderCreatedEvent}/{@code OrderLineAddedEvent}/{@code OrderPaidEvent}/{@code OrderShippedEvent}/{@code OrderCancelledEvent}</li>
 *       <li>{@code OrderRepository} + {@code InMemoryOrderRepository}</li>
 *       <li>{@code OrderApplicationService}（写侧）+ {@code OrderQueryService}（读侧）</li>
 *       <li>{@code OrderController}（写侧 REST）+ {@code OrderQueryController}（基础读侧） + {@code OrderCQRSQueryController}（CQRS 缓存读侧）</li>
 *     </ul>
 *   </li>
 *   <li><b>Goods 业务（第三轨 / Model/Query CRUD）</b>：
 *     <ul>
 *       <li>{@code Goods} 实体（轻量 PO）、{@code GoodsId}、{@code GoodsStatus}</li>
 *       <li>{@code GoodsQuery} 充血查询对象</li>
 *       <li>{@code GoodsRepository} + {@code InMemoryGoodsRepository}</li>
 *       <li>{@code GoodsApplicationService}</li>
 *       <li>{@code GoodsController}（写侧）+ {@code GoodsQueryController}（基础读侧） + {@code GoodsReadController}（CQRS 缓存读侧）</li>
 *     </ul>
 *   </li>
 *   <li><b>CQRS 特色</b>：
 *     <ul>
 *       <li>{@link io.ddd4j.sample.spring.cqrs.cache.OrderCacheService}：订单统计 / 买家订单计数缓存</li>
 *       <li>{@link io.ddd4j.sample.spring.cqrs.cache.GoodsCacheService}：商品详情 / 列表缓存</li>
 *       <li>Spring {@code @EventListener} 桥接 ddd4j 领域事件</li>
 *       <li>{@code @MQEventListener} 订阅订单主题事件</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>REST API 摘要：
 * <ul>
 *   <li>POST   /orders               创建订单</li>
 *   <li>POST   /orders/{id}/lines    添加订单行</li>
 *   <li>POST   /orders/{id}/pay      支付</li>
 *   <li>POST   /orders/{id}/ship     发货</li>
 *   <li>POST   /orders/{id}/cancel   取消</li>
 *   <li>GET    /api/orders/query/list        列表（CQRS 读侧）</li>
 *   <li>GET    /api/orders/query/stats       统计（CQRS 缓存）</li>
 *   <li>POST   /api/goods              创建商品</li>
 *   <li>PUT    /api/goods/{id}         更新商品</li>
 *   <li>PUT    /api/goods/{id}/status  改状态</li>
 *   <li>DELETE /api/goods/{id}         删除</li>
 *   <li>GET    /api/goods/query/by-id/{id}    详情（CQRS 缓存）</li>
 *   <li>GET    /api/goods/query/list          列表（CQRS 缓存）</li>
 *   <li>GET    /api/goods/query/count         计数（CQRS 缓存）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@SpringBootApplication
@Import({SpringContextBridge.class, SpringDomainEventPublisher.class, GlobalRestExceptionAdvice.class})
public class SpringCqrsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringCqrsApplication.class, args);
    }
}
