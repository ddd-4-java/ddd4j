/*
package io.hiwepy.boot.autoconfigure;

import io.hiwepy.boot.autoconfigure.kafka.KafkaAdminTemplate;
import io.hiwepy.boot.autoconfigure.kafka.KafkaConsumerTemplate;
import io.hiwepy.boot.autoconfigure.kafka.KafkaProducerTemplate;
import io.hiwepy.boot.autoconfigure.kafka.MyConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.autoconfigure.thread.Threading;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;
import org.springframework.kafka.support.LoggingProducerListener;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.kafka.support.converter.BatchMessageConverter;
import org.springframework.kafka.support.converter.BatchMessagingMessageConverter;
import org.springframework.kafka.support.converter.MessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.transaction.KafkaAwareTransactionManager;
import org.springframework.kafka.transaction.KafkaTransactionManager;

import java.util.function.Function;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties({ KafkaProperties.class, KafkaEnhanceProperties.class , KafkaBackupProperties.class })
public class DefaultKafkaAutoConfiguration {

    @Bean
    public KafkaAdminTemplate kafkaAdminTemplate(KafkaProperties properties) {
        return new KafkaAdminTemplate(properties);
    }

    @Bean
    public KafkaConsumerTemplate kafkaConsumerTemplate(KafkaProperties properties, KafkaEnhanceProperties enhanceProperties) {
        return new KafkaConsumerTemplate(properties, enhanceProperties);
    }

    @Bean
    public KafkaProducerTemplate kafkaProducerTemplate(KafkaProperties properties) {
        return new KafkaProducerTemplate(properties);
    }
    */
/**
 * 自定义Kafka生产者监听器（覆盖默认的Kafka生产者监听器）
 *
 * @return 自定义的Kafka生产者监听器
 *//*

    @Bean
    public ProducerListener<String, String> kafkaProducerListener() {
        return new LoggingProducerListener<>();
    }

    */
/**
 * 自定义一个Kafka生产者工厂（非事务消息,覆盖默认的Kafka生产者工厂）
 *
 * @param customizers 自定义生产者工厂的配置
 * @return 自定义的Kafka生产者工厂
 *//*

    @Bean("kafkaProducerFactory")
    public ProducerFactory<String, String> kafkaProducerFactory(
            KafkaProducerTemplate kafkaProducerTemplate,
            ObjectProvider<DefaultKafkaProducerFactoryCustomizer> customizers) {
        DefaultKafkaProducerFactory<String, String> factory = kafkaProducerTemplate.createProducerFactory();
        customizers.orderedStream().forEach((customizer) -> customizer.customize(factory));
        return factory;
    }

    */
/**
 * 自定义Kafka模板（覆盖默认的Kafka模板）
 *
 * @param kafkaProducerListener 生产者监听器
 * @param messageConverter 消息转换器
 * @return Kafka模板
 *//*

    @Bean("kafkaTemplate")
    public KafkaTemplate<String, String> kafkaTemplate(
            KafkaProducerTemplate kafkaProducerTemplate,
            @Qualifier("kafkaProducerFactory") ProducerFactory<String, String> kafkaProducerFactory,
            ProducerListener<String, String> kafkaProducerListener,
            ObjectProvider<RecordMessageConverter> messageConverter) {
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(kafkaProducerFactory);
        messageConverter.ifUnique(kafkaTemplate::setMessageConverter);
        kafkaTemplate.setProducerListener(kafkaProducerListener);
        kafkaTemplate.setDefaultTopic(kafkaProducerTemplate.getDefaultTopic());
        return kafkaTemplate;
    }

    */
/**
 * 自定义一个Kafka事务生产者工厂（专用于事务消息的发送）
 *
 * @param customizers 自定义生产者工厂的配置
 * @return 自定义的Kafka事务生产者工厂
 *//*

    @Bean("kafkaTsProducerFactory")
    public ProducerFactory<String, String> kafkaTsProducerFactory(
            KafkaProducerTemplate kafkaProducerTemplate,
            ObjectProvider<DefaultKafkaProducerFactoryCustomizer> customizers) {
        DefaultKafkaProducerFactory<String, String> factory = kafkaProducerTemplate.createTransactionProducerFactory();
        customizers.orderedStream().forEach((customizer) -> customizer.customize(factory));
        return factory;
    }

    */
/**
 * 自定义一个Kafka事务模板（专用于事务消息的发送）
 *
 * @param kafkaProducerFactory 事务生产者工厂
 * @return 自定义的Kafka事务模板
 *//*

    @Bean("kafkaTsTemplate")
    public KafkaTemplate<String, String> kafkaTsTemplate(
            KafkaProducerTemplate kafkaProducerTemplate,
            @Qualifier("kafkaTsProducerFactory") ProducerFactory<String, String> kafkaProducerFactory,
            ProducerListener<String, String> kafkaProducerListener,
            ObjectProvider<RecordMessageConverter> messageConverter) {
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(kafkaProducerFactory);
        messageConverter.ifUnique(kafkaTemplate::setMessageConverter);
        kafkaTemplate.setProducerListener(kafkaProducerListener);
        kafkaTemplate.setDefaultTopic(kafkaProducerTemplate.getDefaultTopic());
        return kafkaTemplate;
    }

    */
/**
 * 自定义一个Kafka事务管理器（覆盖默认的Kafka事务管理器）
 * @param producerFactory 事务生产者工厂
 * @return 自定义的Kafka事务管理器
 *//*

    @Bean
    @ConditionalOnProperty(name = "spring.kafka.producer.transaction-id-prefix")
    public KafkaTransactionManager<String, String> kafkaTransactionManager(
            @Qualifier("kafkaTsProducerFactory") ProducerFactory<String, String> producerFactory) {
        return new KafkaTransactionManager<>(producerFactory);
    }

    */
/**
 * 自定义Kafka消费者工厂（非事务,覆盖默认的Kafka消费者工厂）
 * @param customizers 自定义消费者工厂的配置
 * @return 自定义的Kafka消费者工厂
 *//*

    @Bean("kafkaConsumerFactory")
    public ConsumerFactory<String, String> kafkaConsumerFactory(
            KafkaConsumerTemplate kafkaConsumerTemplate,
            ObjectProvider<DefaultKafkaConsumerFactoryCustomizer> customizers) {
        DefaultKafkaConsumerFactory<String, String> factory = kafkaConsumerTemplate.createConsumerFactory();
        customizers.orderedStream().forEach((customizer) -> customizer.customize(factory));
        return factory;
    }


    @Bean
    @ConditionalOnThreading(Threading.PLATFORM)
    MyConcurrentKafkaListenerContainerFactoryConfigurer kafkaListenerContainerFactoryConfigurer(KafkaProperties properties,
                                                                                              ObjectProvider<RecordMessageConverter> recordMessageConverter,
                                                                                              ObjectProvider<RecordFilterStrategy<Object, Object>> recordFilterStrategy,
                                                                                              ObjectProvider<BatchMessageConverter> batchMessageConverter,
                                                                                              ObjectProvider<KafkaTemplate<Object, Object>> kafkaTemplate,
                                                                                              ObjectProvider<KafkaAwareTransactionManager<Object, Object>> kafkaTransactionManager,
                                                                                              ObjectProvider<ConsumerAwareRebalanceListener> rebalanceListener,
                                                                                              ObjectProvider<CommonErrorHandler> commonErrorHandler,
                                                                                              ObjectProvider<AfterRollbackProcessor<Object, Object>> afterRollbackProcessor,
                                                                                              ObjectProvider<RecordInterceptor<Object, Object>> recordInterceptor,
                                                                                              ObjectProvider<BatchInterceptor<Object, Object>> batchInterceptor,
                                                                                              ObjectProvider<Function<MessageListenerContainer, String>> threadNameSupplier) {
        return configurer(properties, recordMessageConverter, recordFilterStrategy,
                batchMessageConverter, kafkaTemplate, kafkaTransactionManager, rebalanceListener,
                commonErrorHandler, afterRollbackProcessor, recordInterceptor, batchInterceptor,
                threadNameSupplier);
    }

    @Bean(name = "kafkaListenerContainerFactoryConfigurer")
    @ConditionalOnThreading(Threading.VIRTUAL)
    MyConcurrentKafkaListenerContainerFactoryConfigurer kafkaListenerContainerFactoryConfigurerVirtualThreads(KafkaProperties properties,
                                                                                                            ObjectProvider<RecordMessageConverter> recordMessageConverter,
                                                                                                            ObjectProvider<RecordFilterStrategy<Object, Object>> recordFilterStrategy,
                                                                                                            ObjectProvider<BatchMessageConverter> batchMessageConverter,
                                                                                                            ObjectProvider<KafkaTemplate<Object, Object>> kafkaTemplate,
                                                                                                            ObjectProvider<KafkaAwareTransactionManager<Object, Object>> kafkaTransactionManager,
                                                                                                            ObjectProvider<ConsumerAwareRebalanceListener> rebalanceListener,
                                                                                                            ObjectProvider<CommonErrorHandler> commonErrorHandler,
                                                                                                            ObjectProvider<AfterRollbackProcessor<Object, Object>> afterRollbackProcessor,
                                                                                                            ObjectProvider<RecordInterceptor<Object, Object>> recordInterceptor,
                                                                                                            ObjectProvider<BatchInterceptor<Object, Object>> batchInterceptor,
                                                                                                            ObjectProvider<Function<MessageListenerContainer, String>> threadNameSupplier) {
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer = configurer(properties, recordMessageConverter, recordFilterStrategy,
                batchMessageConverter, kafkaTemplate, kafkaTransactionManager, rebalanceListener,
                commonErrorHandler, afterRollbackProcessor, recordInterceptor, batchInterceptor,
                threadNameSupplier);
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("kafka-");
        executor.setVirtualThreads(true);
        configurer.setListenerTaskExecutor(executor);
        return configurer;
    }

    private MyConcurrentKafkaListenerContainerFactoryConfigurer configurer(KafkaProperties properties,
                                                                         ObjectProvider<RecordMessageConverter> recordMessageConverterProvider,
                                                                         ObjectProvider<RecordFilterStrategy<String, String>> recordFilterStrategyProvider,
                                                                         ObjectProvider<BatchMessageConverter> batchMessageConverterProvider,
                                                                         ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
                                                                         ObjectProvider<KafkaAwareTransactionManager<Object, Object>> kafkaTransactionManagerProvider,
                                                                         ObjectProvider<ConsumerAwareRebalanceListener> rebalanceListenerProvider,
                                                                         ObjectProvider<CommonErrorHandler> commonErrorHandlerProvider,
                                                                         ObjectProvider<AfterRollbackProcessor<String, String>> afterRollbackProcessorProvider,
                                                                         ObjectProvider<RecordInterceptor<String, String>> recordInterceptorProvider,
                                                                         ObjectProvider<BatchInterceptor<String, String>> batchInterceptorProvider,
                                                                         ObjectProvider<Function<MessageListenerContainer, String>> threadNameSupplierProvider) {


       RecordMessageConverter recordMessageConverter = recordMessageConverterProvider.getIfUnique();;
       RecordFilterStrategy<String, String> recordFilterStrategy = recordFilterStrategyProvider.getIfUnique();
       BatchMessageConverter batchMessageConverter = batchMessageConverterProvider
                .getIfUnique(() -> new BatchMessagingMessageConverter(recordMessageConverter));
       KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfUnique();
       KafkaAwareTransactionManager<Object, Object> transactionManager = kafkaTransactionManagerProvider.getIfUnique();
       ConsumerAwareRebalanceListener rebalanceListener = rebalanceListenerProvider.getIfUnique();
       CommonErrorHandler commonErrorHandler = commonErrorHandlerProvider.getIfUnique();
       AfterRollbackProcessor<String, String> afterRollbackProcessor = afterRollbackProcessorProvider.getIfUnique();
       RecordInterceptor<String, String> recordInterceptor = recordInterceptorProvider.getIfUnique();
       BatchInterceptor<String, String> batchInterceptor = batchInterceptorProvider.getIfUnique();
       Function<MessageListenerContainer, String> threadNameSupplier = threadNameSupplierProvider.getIfUnique();

        MyConcurrentKafkaListenerContainerFactoryConfigurer configurer = new MyConcurrentKafkaListenerContainerFactoryConfigurer();
        configurer.setKafkaProperties(properties);
        configurer.setBatchMessageConverter(batchMessageConverter);
        configurer.setRecordMessageConverter(recordMessageConverter);
        configurer.setRecordFilterStrategy(recordFilterStrategy);
        configurer.setReplyTemplate(kafkaTemplate);
        configurer.setTransactionManager(transactionManager);
        configurer.setRebalanceListener(rebalanceListener);
        configurer.setCommonErrorHandler(commonErrorHandler);
        configurer.setAfterRollbackProcessor(afterRollbackProcessor);
        configurer.setRecordInterceptor(recordInterceptor);
        configurer.setBatchInterceptor(batchInterceptor);
        configurer.setThreadNameSupplier(threadNameSupplier);
        return configurer;
    }

    */
/**
 * 自定义Kafka消费者工厂配置器（不初始化事务）
 * @param properties Kafka属性
 * @param messageConverter 消息转换器
 * @param batchMessageConverter 批量消息转换器
 * @param kafkaTemplate Kafka模板
 * @param rebalanceListener Rebalance监听器
 * @param errorHandler 错误处理器
 * @param batchErrorHandler 批量错误处理器
 * @param afterRollbackProcessor 回滚处理器
 * @param recordInterceptor 记录拦截器
 * @return 自定义的Kafka消费者工厂配置器
 *//*

    @Bean("kafkaListenerContainerFactoryConfigurer")
    public ConcurrentKafkaListenerContainerFactoryConfigurer kafkaListenerContainerFactoryConfigurer(KafkaProperties properties,
                                                                                                     ObjectProvider<RecordMessageConverter> messageConverter,
                                                                                                     ObjectProvider<BatchMessageConverter> batchMessageConverter,
                                                                                                     @Qualifier("kafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
                                                                                                     ObjectProvider<ConsumerAwareRebalanceListener> rebalanceListener,
                                                                                                     ObjectProvider<ErrorHandler> errorHandler,
                                                                                                     ObjectProvider<BatchErrorHandler> batchErrorHandler,
                                                                                                     ObjectProvider<AfterRollbackProcessor<String, String>> afterRollbackProcessor,
                                                                                                     ObjectProvider<RecordInterceptor<String, String>> recordInterceptor) {
        MyConcurrentKafkaListenerContainerFactoryConfigurer configurer = new MyConcurrentKafkaListenerContainerFactoryConfigurer();
        configurer.setKafkaProperties(properties);
        MessageConverter messageConverterToUse = (properties.getListener().getType().equals(KafkaProperties.Listener.Type.BATCH))
                ? batchMessageConverter.getIfAvailable() : messageConverter.getIfAvailable();
        configurer.setMessageConverter(messageConverterToUse);
        configurer.setReplyTemplate(kafkaTemplate);
        configurer.setRebalanceListener(rebalanceListener.getIfAvailable());
        configurer.setErrorHandler(errorHandler.getIfAvailable());
        configurer.setBatchErrorHandler(batchErrorHandler.getIfAvailable());
        configurer.setAfterRollbackProcessor(afterRollbackProcessor.getIfAvailable());
        configurer.setRecordInterceptor(recordInterceptor.getIfAvailable());
        // 非事务消费者自动提交偏移量
        configurer.setAckMode(ContainerProperties.AckMode.BATCH);
        return configurer;
    }

    */
/**
 * 自定义Kafka消费者工厂（非事务,覆盖默认的Kafka消费者工厂）
 * @param configurer 配置器
 * @param kafkaConsumerFactory 消费者工厂
 * @return 自定义的Kafka消费者工厂
 *//*

    @Bean("kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            KafkaConsumerTemplate kafkaConsumerTemplate,
            @Qualifier("kafkaListenerContainerFactoryConfigurer") ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            @Qualifier("kafkaConsumerFactory") ConsumerFactory<String, String>  kafkaConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory = kafkaConsumerTemplate.createKafkaListenerContainerFactory();

        MyConcurrentKafkaListenerContainerFactoryConfigurer myConfigurer = (MyConcurrentKafkaListenerContainerFactoryConfigurer) configurer;
        myConfigurer.configure2(factory, kafkaConsumerFactory);

        return factory;
    }

    // ==========================================

    */
/**
 * 自定义一个Kafka事务消费者工厂（专用于事务消息）
 * @param customizers 自定义消费者工厂的配置
 * @return 自定义的Kafka事务消费者工厂
 *//*

    @Bean("kafkaTsConsumerFactory")
    public ConsumerFactory<String, String> kafkaTransactionConsumerFactory(
            KafkaConsumerTemplate kafkaConsumerTemplate,
            ObjectProvider<DefaultKafkaConsumerFactoryCustomizer> customizers) {
        DefaultKafkaConsumerFactory<String, String> factory = kafkaConsumerTemplate.createTransactionConsumerFactory();
        customizers.orderedStream().forEach((customizer) -> customizer.customize(factory));
        return factory;
    }


    */
/**
 * 自定义Kafka消费者工厂配置器（初始化事务）
 * @param properties Kafka属性
 * @param messageConverter 消息转换器
 * @param batchMessageConverter 批量消息转换器
 * @param kafkaTemplate Kafka模板
 * @param rebalanceListener Rebalance监听器
 * @param errorHandler 错误处理器
 * @param batchErrorHandler 批量错误处理器
 * @param afterRollbackProcessor 回滚处理器
 * @param recordInterceptor 记录拦截器
 * @return 自定义的Kafka消费者工厂配置器
 *//*

    @Bean("kafkaTsListenerContainerFactoryConfigurer")
    public MyConcurrentKafkaListenerContainerFactoryConfigurer kafkaTransactionListenerContainerFactoryConfigurer(KafkaProperties properties,
                                                                                                                  ObjectProvider<RecordMessageConverter> messageConverter,
                                                                                                                  ObjectProvider<BatchMessageConverter> batchMessageConverter,
                                                                                                                  @Qualifier("kafkaTsTemplate") KafkaTemplate<String, String> kafkaTemplate,
                                                                                                                  ObjectProvider<KafkaAwareTransactionManager<String, String>> kafkaTransactionManager,
                                                                                                                  ObjectProvider<ConsumerAwareRebalanceListener> rebalanceListener,
                                                                                                                  @Qualifier("kafkaTsErrorHandler") ErrorHandler errorHandler,
                                                                                                                  @Qualifier("kafkaTsBatchErrorHandler") BatchErrorHandler batchErrorHandler,
                                                                                                                  ObjectProvider<AfterRollbackProcessor<String, String>> afterRollbackProcessor,
                                                                                                                  ObjectProvider<RecordInterceptor<String, String>> recordInterceptor) {
        MyConcurrentKafkaListenerContainerFactoryConfigurer configurer = new MyConcurrentKafkaListenerContainerFactoryConfigurer();
        configurer.setKafkaProperties(properties);
        MessageConverter messageConverterToUse = (properties.getListener().getType().equals(KafkaProperties.Listener.Type.BATCH))
                ? batchMessageConverter.getIfAvailable() : messageConverter.getIfAvailable();
        configurer.setMessageConverter(messageConverterToUse);
        configurer.setReplyTemplate(kafkaTemplate);
        configurer.setTransactionManager(kafkaTransactionManager.getIfAvailable());
        configurer.setRebalanceListener(rebalanceListener.getIfAvailable());
        configurer.setErrorHandler(errorHandler);
        configurer.setBatchErrorHandler(batchErrorHandler);
        configurer.setAfterRollbackProcessor(afterRollbackProcessor.getIfAvailable());
        configurer.setRecordInterceptor(recordInterceptor.getIfAvailable());
        // 事务消费者需要手动提交偏移量
        configurer.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return configurer;
    }

    DefaultKafkaAutoConfiguration

    */
/**
 * 自定义Kafka消费者工厂（事务）
 * @return 自定义的Kafka消费者工厂
 *//*

    @Bean("kafkaTsListenerContainerFactory")
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>> kafkaTransactionListenerContainerFactory(
            KafkaConsumerTemplate kafkaConsumerTemplate,
            @Qualifier("kafkaTsListenerContainerFactoryConfigurer") ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            @Qualifier("kafkaTsConsumerFactory") ConsumerFactory<String, String>  kafkaConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory = kafkaConsumerTemplate.createkafkaTsListenerContainerFactory();
        MyConcurrentKafkaListenerContainerFactoryConfigurer myConfigurer = (MyConcurrentKafkaListenerContainerFactoryConfigurer) configurer;
        myConfigurer.configure2(factory, kafkaConsumerFactory);
        return factory;
    }

}
*/
