package io.ddd4j.boot.cmpt.kafka.consumer;


import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties.Listener;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;
import org.springframework.kafka.support.converter.BatchMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.transaction.KafkaAwareTransactionManager;

import java.time.Duration;
import java.util.function.Function;

/**
 * Configure {@link ConcurrentKafkaListenerContainerFactory} with sensible defaults.
 *
 * @author Gary Russell
 * @author Eddú Meléndez
 * @since 1.5.0
 */
public class MyConcurrentKafkaListenerContainerFactoryConfigurer extends ConcurrentKafkaListenerContainerFactoryConfigurer {

    private KafkaProperties properties;

    private BatchMessageConverter batchMessageConverter;

    private RecordMessageConverter recordMessageConverter;

    private RecordFilterStrategy<String, String> recordFilterStrategy;

    private KafkaTemplate<String, String> replyTemplate;

    private KafkaAwareTransactionManager<String, String> transactionManager;

    private ConsumerAwareRebalanceListener rebalanceListener;

    private CommonErrorHandler commonErrorHandler;

    private AfterRollbackProcessor<String, String> afterRollbackProcessor;

    private RecordInterceptor<String, String> recordInterceptor;

    private BatchInterceptor<String, String> batchInterceptor;

    private Function<MessageListenerContainer, String> threadNameSupplier;

    private SimpleAsyncTaskExecutor listenerTaskExecutor;

    /**
     * The ack mode to use when auto ack (in the configuration properties) is false.
     * <ul>
     * <li>RECORD: Ack after each record has been passed to the listener.</li>
     * <li>BATCH: Ack after each batch of records received from the consumer has been
     * passed to the listener</li>
     * <li>TIME: Ack after this number of milliseconds; (should be greater than
     * {@code #setPollTimeout(long) pollTimeout}.</li>
     * <li>COUNT: Ack after at least this number of records have been received</li>
     * <li>MANUAL: Listener is responsible for acking - use a
     * {@link AcknowledgingMessageListener}.
     * </ul>
     */
    private ContainerProperties.AckMode ackMode;


    /**
     * Set the {@link KafkaProperties} to use.
     *
     * @param properties the properties
     */
    void setKafkaProperties(KafkaProperties properties) {
        this.properties = properties;
    }

    /**
     * Set the {@link BatchMessageConverter} to use.
     *
     * @param batchMessageConverter the message converter
     */
    void setBatchMessageConverter(BatchMessageConverter batchMessageConverter) {
        this.batchMessageConverter = batchMessageConverter;
    }

    /**
     * Set the {@link RecordMessageConverter} to use.
     *
     * @param recordMessageConverter the message converter
     */
    void setRecordMessageConverter(RecordMessageConverter recordMessageConverter) {
        this.recordMessageConverter = recordMessageConverter;
    }

    /**
     * Set the {@link RecordFilterStrategy} to use to filter incoming records.
     *
     * @param recordFilterStrategy the record filter strategy
     */
    void setRecordFilterStrategy(RecordFilterStrategy<String, String> recordFilterStrategy) {
        this.recordFilterStrategy = recordFilterStrategy;
    }

    /**
     * Set the {@link KafkaTemplate} to use to send replies.
     *
     * @param replyTemplate the reply template
     */
    void setReplyTemplate(KafkaTemplate<String, String> replyTemplate) {
        this.replyTemplate = replyTemplate;
    }

    /**
     * Set the {@link KafkaAwareTransactionManager} to use.
     *
     * @param transactionManager the transaction manager
     */
    void setTransactionManager(KafkaAwareTransactionManager<String, String> transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * Set the {@link ConsumerAwareRebalanceListener} to use.
     *
     * @param rebalanceListener the rebalance listener.
     * @since 2.2
     */
    void setRebalanceListener(ConsumerAwareRebalanceListener rebalanceListener) {
        this.rebalanceListener = rebalanceListener;
    }

    /**
     * Set the {@link CommonErrorHandler} to use.
     *
     * @param commonErrorHandler the error handler.
     * @since 2.6.0
     */
    public void setCommonErrorHandler(CommonErrorHandler commonErrorHandler) {
        this.commonErrorHandler = commonErrorHandler;
    }

    /**
     * Set the {@link AfterRollbackProcessor} to use.
     *
     * @param afterRollbackProcessor the after rollback processor
     */
    void setAfterRollbackProcessor(AfterRollbackProcessor<String, String> afterRollbackProcessor) {
        this.afterRollbackProcessor = afterRollbackProcessor;
    }

    /**
     * Set the {@link RecordInterceptor} to use.
     *
     * @param recordInterceptor the record interceptor.
     */
    void setRecordInterceptor(RecordInterceptor<String, String> recordInterceptor) {
        this.recordInterceptor = recordInterceptor;
    }

    /**
     * Set the {@link BatchInterceptor} to use.
     *
     * @param batchInterceptor the batch interceptor.
     */
    void setBatchInterceptor(BatchInterceptor<String, String> batchInterceptor) {
        this.batchInterceptor = batchInterceptor;
    }

    /**
     * Set the thread name supplier to use.
     *
     * @param threadNameSupplier the thread name supplier to use
     */
    void setThreadNameSupplier(Function<MessageListenerContainer, String> threadNameSupplier) {
        this.threadNameSupplier = threadNameSupplier;
    }

    /**
     * Set the executor for threads that poll the consumer.
     *
     * @param listenerTaskExecutor task executor
     */
    void setListenerTaskExecutor(SimpleAsyncTaskExecutor listenerTaskExecutor) {
        this.listenerTaskExecutor = listenerTaskExecutor;
    }

    public void setAckMode(ContainerProperties.AckMode ackMode) {
        this.ackMode = ackMode;
    }


    /**
     * Configure the specified Kafka listener container factory. The factory can be
     * further tuned and default settings can be overridden.
     *
     * @param listenerFactory the {@link ConcurrentKafkaListenerContainerFactory} instance
     *                        to configure
     * @param consumerFactory the {@link ConsumerFactory} to use
     */
    public void configure2(ConcurrentKafkaListenerContainerFactory<String, String> listenerFactory,
                           ConsumerFactory<String, String> consumerFactory) {
        listenerFactory.setConsumerFactory(consumerFactory);
        configureListenerFactory2(listenerFactory);
        configureContainer(consumerFactory, listenerFactory.getContainerProperties());
    }

    private void configureListenerFactory2(ConcurrentKafkaListenerContainerFactory<String, String> factory) {
        PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
        Listener properties = this.properties.getListener();
        map.from(properties::getConcurrency).to(factory::setConcurrency);
        map.from(properties::isAutoStartup).to(factory::setAutoStartup);
        map.from(this.batchMessageConverter).to(factory::setBatchMessageConverter);
        map.from(this.recordMessageConverter).to(factory::setRecordMessageConverter);
        map.from(this.recordFilterStrategy).to(factory::setRecordFilterStrategy);
        map.from(this.replyTemplate).to(factory::setReplyTemplate);
        if (properties.getType().equals(Listener.Type.BATCH)) {
            factory.setBatchListener(true);
        }
        map.from(this.commonErrorHandler).to(factory::setCommonErrorHandler);
        map.from(this.afterRollbackProcessor).to(factory::setAfterRollbackProcessor);
        map.from(this.recordInterceptor).to(factory::setRecordInterceptor);
        map.from(this.batchInterceptor).to(factory::setBatchInterceptor);
        map.from(this.threadNameSupplier).to(factory::setThreadNameSupplier);
        map.from(properties::getChangeConsumerThreadName).to(factory::setChangeConsumerThreadName);
    }

    private void configureContainer(ConsumerFactory<String, String> consumerFactory, ContainerProperties container) {
        PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
        Listener properties = this.properties.getListener();
        // 如果是自动提交，且ackMode是MANUAL或MANUAL_IMMEDIATE，那么ackMode需要手动设置
        if (consumerFactory.isAutoCommit() && (properties.getAckMode() == ContainerProperties.AckMode.MANUAL
                || properties.getAckMode() == ContainerProperties.AckMode.MANUAL_IMMEDIATE)) {
            map.from(this.ackMode).to(container::setAckMode);
        } else {
            map.from(properties::getAckMode).to(container::setAckMode);
        }
        map.from(properties::getAsyncAcks).to(container::setAsyncAcks);
        map.from(properties::getClientId).to(container::setClientId);
        map.from(properties::getAckCount).to(container::setAckCount);
        map.from(properties::getAckTime).as(Duration::toMillis).to(container::setAckTime);
        map.from(properties::getPollTimeout).as(Duration::toMillis).to(container::setPollTimeout);
        map.from(properties::getNoPollThreshold).to(container::setNoPollThreshold);
        map.from(properties.getIdleBetweenPolls()).as(Duration::toMillis).to(container::setIdleBetweenPolls);
        map.from(properties::getIdleEventInterval).as(Duration::toMillis).to(container::setIdleEventInterval);
        map.from(properties::getIdlePartitionEventInterval)
                .as(Duration::toMillis)
                .to(container::setIdlePartitionEventInterval);
        map.from(properties::getMonitorInterval)
                .as(Duration::getSeconds)
                .as(Number::intValue)
                .to(container::setMonitorInterval);
        map.from(properties::getLogContainerConfig).to(container::setLogContainerConfig);
        map.from(properties::isMissingTopicsFatal).to(container::setMissingTopicsFatal);
        map.from(properties::isImmediateStop).to(container::setStopImmediate);
        map.from(properties::isObservationEnabled).to(container::setObservationEnabled);
        map.from(this.transactionManager).to(container::setKafkaAwareTransactionManager);
        map.from(this.rebalanceListener).to(container::setConsumerRebalanceListener);
        map.from(this.listenerTaskExecutor).to(container::setListenerTaskExecutor);
    }

}
