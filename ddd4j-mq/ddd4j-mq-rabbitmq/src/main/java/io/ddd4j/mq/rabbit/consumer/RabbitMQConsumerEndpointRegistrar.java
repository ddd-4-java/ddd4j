package io.ddd4j.mq.rabbit.consumer;

import com.rabbitmq.client.Channel;
import io.ddd4j.mq.rabbit.ack.AmqpMessageAcknowledgmentFactory;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.ack.NoOpMessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.registry.MQListenerDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 将 {@code @MQEventListener} 动态注册为 RabbitMQ 消费端点。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class RabbitMQConsumerEndpointRegistrar {

    private final ApplicationContext applicationContext;
    private final RabbitListenerEndpointRegistry endpointRegistry;
    private final Ddd4jMQProperties properties;
    private final List<MQListenerDefinition> registeredDefinitions = new CopyOnWriteArrayList<>();

    /**
     * 注册单个监听器定义。
     *
     * @param definition 监听器定义
     * @param handler    消费处理函数
     */
    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(handler, "handler");

        RabbitTemplate rabbitTemplate = applicationContext.getBean(RabbitTemplate.class);
        RabbitAdmin rabbitAdmin = resolveRabbitAdmin(rabbitTemplate);
        RabbitListenerContainerFactory<?> containerFactory = resolveContainerFactory();

        String concat = resolveConcat(definition);
        String queueName = buildQueueName(definition, concat);
        String exchangeName = rabbitTemplate.getExchange();
        String routingKey = buildRoutingKey(definition, concat);

        declareTopology(rabbitAdmin, queueName, exchangeName, routingKey);

        SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
        endpoint.setId(buildEndpointId(definition));
        endpoint.setQueueNames(queueName);
        endpoint.setAckMode(properties.getConsumer().isManualAck()
                ? AcknowledgeMode.MANUAL
                : AcknowledgeMode.AUTO);
        endpoint.setMessageListener(createMessageListener(definition, handler));

        endpointRegistry.registerListenerContainer(endpoint, containerFactory, true);
        registeredDefinitions.add(definition);

        log.info("Registered RabbitMQ listener endpoint: id={}, queue={}, routingKey={}, ackMode={}",
                endpoint.getId(), queueName, routingKey, properties.getConsumer().getAckMode());
    }

    /**
     * 批量注册监听器（启动阶段调用）。
     */
    public void registerAll(List<MQListenerDefinition> definitions, MQConsumerHandler handler) {
        if (definitions == null || definitions.isEmpty()) {
            log.debug("No @MQEventListener definitions found for RabbitMQ");
            return;
        }
        for (MQListenerDefinition definition : definitions) {
            register(definition, handler);
        }
        log.info("RabbitMQ consumer registrar initialized with {} listener(s)", registeredDefinitions.size());
    }

    /**
     * 返回已登记的监听器定义（只读视图）。
     */
    public List<MQListenerDefinition> registeredDefinitions() {
        return List.copyOf(registeredDefinitions);
    }

    /**
     * 创建 ChannelAware 消息监听器，转换 AMQP 消息并委托 {@link MQConsumerHandler}。
     */
    private ChannelAwareMessageListener createMessageListener(
            MQListenerDefinition definition,
            MQConsumerHandler handler) {

        return (Message amqpMessage, Channel channel) -> {
            try {
                String payloadText = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);

                // 2.0.x：直接构造纯 Java MQMessage，Channel/deliveryTag 通过 headers 传递（与 AmqpMessageAcknowledgmentFactory.from(MQMessage) 配套）
                Map<String, Object> headers = new HashMap<>();
                Map<String, Object> propsHeaders = amqpMessage.getMessageProperties().getHeaders();
                if (propsHeaders != null) {
                    headers.putAll(propsHeaders);
                }
                headers.put(AmqpHeaders.CHANNEL, channel);
                headers.put(AmqpHeaders.DELIVERY_TAG, amqpMessage.getMessageProperties().getDeliveryTag());

                MQMessage<String> mqMessage = MQMessage.of(
                        payloadText,
                        headers,
                        amqpMessage.getMessageProperties().getMessageId(),
                        amqpMessage.getMessageProperties().getCorrelationId(),
                        amqpMessage);

                MessageAcknowledgment ack = AmqpMessageAcknowledgmentFactory.from(mqMessage)
                        .map(a -> (MessageAcknowledgment) a)
                        .orElseGet(NoOpMessageAcknowledgment::new);

                handler.handle(mqMessage, ack);
            } catch (Exception ex) {
                log.error("RabbitMQ consumer failed: bean={}, method={}",
                        definition.getBean().getClass().getSimpleName(),
                        definition.getMethod().getName(),
                        ex);
                if (properties.getConsumer().isManualAck() && channel != null && channel.isOpen()) {
                    try {
                        channel.basicNack(amqpMessage.getMessageProperties().getDeliveryTag(), false, true);
                    } catch (Exception nackEx) {
                        log.warn("Failed to nack RabbitMQ message after consumer error", nackEx);
                    }
                }
            }
        };
    }

    /**
     * 声明队列、交换机与绑定关系。
     */
    private void declareTopology(RabbitAdmin rabbitAdmin, String queueName, String exchangeName, String routingKey) {
        Queue queue = new Queue(queueName, true);
        DirectExchange exchange = new DirectExchange(exchangeName, true, false);
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareExchange(exchange);
        rabbitAdmin.declareBinding(binding);
    }

    /**
     * 构建队列名：group.namespace.className.methodName。
     */
    private String buildQueueName(MQListenerDefinition definition, String concat) {
        String group = definition.getGroup();
        String namespace = definition.getNamespace();
        String className = definition.getMethod().getDeclaringClass().getSimpleName();
        String methodName = definition.getMethod().getName();
        return group + concat + namespace + concat + className + concat + methodName;
    }

    /**
     * 构建路由键：namespace.topic[.tag]。
     */
    private String buildRoutingKey(MQListenerDefinition definition, String concat) {
        String namespace = definition.getNamespace();
        String topic = definition.getTopic();
        String tags = definition.getTags();
        if (StringUtils.hasText(tags) && !"*".equals(tags.trim())) {
            String tag = tags.contains("||") ? tags.substring(0, tags.indexOf("||")).trim() : tags.trim();
            return namespace + concat + topic + concat + tag;
        }
        return namespace + concat + topic;
    }

    /**
     * 解析连接符，默认 {@code .}。
     */
    private String resolveConcat(MQListenerDefinition definition) {
        if (StringUtils.hasText(definition.getConcat())) {
            return definition.getConcat();
        }
        return ".";
    }

    /**
     * 构建端点 ID，保证在 registry 内唯一。
     */
    private String buildEndpointId(MQListenerDefinition definition) {
        return "ddd4j-" + definition.bindingName() + "-"
                + definition.getMethod().getDeclaringClass().getSimpleName() + "-"
                + definition.getMethod().getName();
    }

    /**
     * 解析 RabbitAdmin，不存在时基于 RabbitTemplate 创建。
     */
    private RabbitAdmin resolveRabbitAdmin(RabbitTemplate rabbitTemplate) {
        if (applicationContext.containsBean("rabbitAdmin")) {
            return applicationContext.getBean(RabbitAdmin.class);
        }
        return new RabbitAdmin(rabbitTemplate);
    }

    /**
     * 解析 RabbitListenerContainerFactory Bean。
     */
    @SuppressWarnings("rawtypes")
    private RabbitListenerContainerFactory resolveContainerFactory() {
        if (applicationContext.containsBean("rabbitListenerContainerFactory")) {
            return applicationContext.getBean("rabbitListenerContainerFactory", RabbitListenerContainerFactory.class);
        }
        return applicationContext.getBean(RabbitListenerContainerFactory.class);
    }
}
