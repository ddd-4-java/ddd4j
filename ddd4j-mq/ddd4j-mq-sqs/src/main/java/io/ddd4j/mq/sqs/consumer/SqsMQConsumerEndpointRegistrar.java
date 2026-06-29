package io.ddd4j.mq.sqs.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import io.ddd4j.mq.ack.MessageAcknowledgment;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.consume.MQConsumerHandler;
import io.ddd4j.mq.contract.MQMessage;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQListenerEndpointNaming;
import io.ddd4j.mq.sqs.ack.SqsMessageAcknowledgment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将 {@code @MQEventListener} 动态注册为 SQS 长轮询消费循环。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class SqsMQConsumerEndpointRegistrar implements AutoCloseable {

    private static final int MAX_MESSAGES = 10;
    private static final int WAIT_TIME_SECONDS = 20;

    private final AmazonSQS amazonSqs;
    private final String defaultQueueUrl;
    private final Ddd4jMQProperties properties;
    private final List<MQListenerDefinition> registeredDefinitions = new CopyOnWriteArrayList<>();
    private final List<Future<?>> pollingTasks = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "ddd4j-sqs-consumer");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 注册单个监听器定义。
     */
    public void register(MQListenerDefinition definition, MQConsumerHandler handler) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(handler, "handler");

        String queueUrl = resolveQueueUrl(definition);
        if (!StringUtils.hasText(queueUrl)) {
            log.warn("Skip SQS listener registration: queue URL not configured for topic={}",
                    definition.getTopic());
            return;
        }

        Future<?> task = executor.submit(() -> pollLoop(queueUrl, definition, handler));
        pollingTasks.add(task);
        registeredDefinitions.add(definition);

        log.info("Registered SQS polling listener: queueUrl={}, topic={}, ackMode={}",
                queueUrl, definition.getTopic(), properties.getConsumer().getAckMode());
    }

    /**
     * 批量注册监听器。
     */
    public void registerAll(List<MQListenerDefinition> definitions, MQConsumerHandler handler) {
        if (definitions == null || definitions.isEmpty()) {
            log.debug("No @MQEventListener definitions found for SQS");
            return;
        }
        for (MQListenerDefinition definition : definitions) {
            register(definition, handler);
        }
        log.info("SQS consumer registrar initialized with {} listener(s)", registeredDefinitions.size());
    }

    @Override
    public void close() {
        running.set(false);
        for (Future<?> task : pollingTasks) {
            task.cancel(true);
        }
        pollingTasks.clear();
        executor.shutdownNow();
    }

    /**
     * 返回已登记的监听器定义。
     */
    public List<MQListenerDefinition> registeredDefinitions() {
        return List.copyOf(registeredDefinitions);
    }

    /**
     * 长轮询消费循环。
     */
    private void pollLoop(String queueUrl, MQListenerDefinition definition, MQConsumerHandler handler) {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                ReceiveMessageResult result = amazonSqs.receiveMessage(new ReceiveMessageRequest(queueUrl)
                        .withMaxNumberOfMessages(MAX_MESSAGES)
                        .withWaitTimeSeconds(WAIT_TIME_SECONDS)
                        .withAttributeNames("All")
                        .withMessageAttributeNames("All"));
                for (Message sqsMessage : result.getMessages()) {
                    onMessage(queueUrl, sqsMessage, definition, handler);
                }
            } catch (Exception ex) {
                if (running.get()) {
                    log.error("SQS polling failed: queueUrl={}", queueUrl, ex);
                    sleepQuietly(1_000L);
                }
            }
        }
    }

    /**
     * 处理单条 SQS 消息。
     */
    private void onMessage(
            String queueUrl,
            Message sqsMessage,
            MQListenerDefinition definition,
            MQConsumerHandler handler) {

        try {
            String payloadText = sqsMessage.getBody();
            Map<String, Object> headers = new HashMap<>();
            if (sqsMessage.getMessageAttributes() != null) {
                sqsMessage.getMessageAttributes().forEach((k, v) -> headers.put(k, v.getStringValue()));
            }

            MQMessage<String> mqMessage = MQMessage.of(
                    payloadText,
                    headers,
                    sqsMessage.getMessageId(),
                    sqsMessage.getReceiptHandle(),
                    sqsMessage);

            MessageAcknowledgment ack = new SqsMessageAcknowledgment(amazonSqs, queueUrl, sqsMessage);
            handler.handle(mqMessage, ack);
            if (!properties.getConsumer().isManualAck() && !ack.isAcknowledged()) {
                ack.ack();
            }
        } catch (Exception ex) {
            log.error("SQS consumer failed: bean={}, method={}",
                    beanLabel(definition), definition.getMethod().getName(), ex);
        }
    }

    /**
     * 解析队列 URL：优先 defaultQueueUrl，否则以 topic 作为队列名拼接（需 IAM 侧已创建）。
     */
    private String resolveQueueUrl(MQListenerDefinition definition) {
        if (StringUtils.hasText(defaultQueueUrl)) {
            return defaultQueueUrl;
        }
        String topic = MQListenerEndpointNaming.physicalTopic(properties, definition);
        if (topic.startsWith("http://") || topic.startsWith("https://")) {
            return topic;
        }
        return null;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String beanLabel(MQListenerDefinition definition) {
        if (definition.getBean() != null) {
            return definition.getBean().getClass().getSimpleName();
        }
        if (definition.getBeanName() != null) {
            return definition.getBeanName();
        }
        return definition.getMethod().getDeclaringClass().getSimpleName();
    }
}
