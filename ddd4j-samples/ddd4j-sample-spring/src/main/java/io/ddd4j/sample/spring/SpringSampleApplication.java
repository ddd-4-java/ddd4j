package io.ddd4j.sample.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ddd4j + Spring Boot 完整 DDD/CQRS 示例启动类。
 *
 * <p>本示例同时演示 ddd4j 的"双轨"业务模型：
 * <ul>
 *   <li><b>第二轨（充血模型）</b> - {@code order} 子模块：
 *     <ul>
 *       <li>充血聚合（{@code Order} 继承 {@code AggregateRoot}）</li>
 *       <li>值对象（{@code Money} 实现 {@code ValueObject}）</li>
 *       <li>领域事件（{@code OrderCreatedEvent} 等继承 {@code DomainEvent}）</li>
 *       <li>仓储模式（{@code OrderRepository} 接口 + {@code InMemoryOrderRepository} 实现）</li>
 *       <li>领域服务（{@code OrderDomainService}）</li>
 *       <li>应用服务（{@code OrderApplicationService} 编排用例）</li>
 *       <li>CQRS 查询（{@code OrderQueryService} + {@code OrderQueryController}）</li>
 *       <li>缓存（{@code CacheKit} 缓存门面）</li>
 *       <li>MQ 事件发布（{@code @MQEventListener} + Disruptor 本地 MQ）</li>
 *     </ul>
 *   </li>
 *   <li><b>第三轨（Model/Query 快速 CRUD）</b> - {@code goods} 子模块：
 *     <ul>
 *       <li>轻量 PO 实体（{@code Goods} + {@code @DomainEntity}）</li>
 *       <li>充血查询对象（{@code GoodsQuery extends Query}）</li>
 *       <li>Goods 仓储（{@code InMemoryGoodsRepository} 实现 {@code Repository}）</li>
 *       <li>Goods 应用服务（{@code GoodsApplicationService}）</li>
 *       <li>RepositoryRegistry 注册（{@code GoodsConfig}）</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@SpringBootApplication
public class SpringSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringSampleApplication.class, args);
    }
}
