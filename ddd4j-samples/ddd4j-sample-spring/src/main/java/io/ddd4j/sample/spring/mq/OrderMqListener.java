package io.ddd4j.sample.spring.mq;

import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.event.MQEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 订单 MQ 消费者（演示跨进程事件）。
 *
 * <p>使用 ddd4j {@link MQEventListener} 注解声明订阅关系：
 * <ul>
 *   <li>{@code topic="ORDER"}：业务主题</li>
 *   <li>{@code tags="*"}：通配所有标签</li>
 * </ul>
 *
 * <p>切换为 Kafka / RabbitMQ / RocketMQ 时：
 * <ul>
 *   <li>本类代码完全无需修改</li>
 *   <li>仅需替换 pom 中的 ddd4j-mq-disruptor 依赖为对应的 ddd4j-mq-* 模块</li>
 *   <li>在 application.yml 中调整 broker 配置</li>
 * </ul>
 *
 * <p>本监听器在收到事件后会记录日志，演示 MQ 消费者模式。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@Component
public class OrderMqListener {

    /**
     * 处理 MQ 中的订单事件。
     *
     * <p>方法参数为 {@link MQEvent} 基类，接收所有 ORDER 主题的事件。
     * 实际生产中可使用具体的事件子类（如 {@code OrderCreatedEvent extends MQEvent}）
     * 以获取强类型载荷。
     *
     * @param event MQ 事件
     */
    @MQEventListener(topic = "ORDER", tags = "*")
    public void onOrderEvent(MQEvent event) {
        log.info("[MQ] Received order event: topic={}, tag={}, msgId={}",
                event.getTopic(), event.getTag(), event.getMsgId());
    }
}
