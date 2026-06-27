package io.ddd4j.mq.kafka.consumer;

import io.ddd4j.mq.kafka.KafkaEnhanceProperties;
import io.ddd4j.mq.kafka.config.KafkaConnectionProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

/**
 * Kafka消费者模板类，提供同步和异步消费功能
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class KafkaConsumerTemplate implements DisposableBean {

    // 消费者缓存，key为consumerId
    private final Map<String, KafkaConsumer<String, String>> CONSUMER_MAP = new ConcurrentHashMap<>();
    // 用于保护消费者创建和移除操作的锁
    private final Map<String, Lock> CONSUMER_LOCKS = new ConcurrentHashMap<>();
    private final KafkaConnectionProperties properties;
    private final KafkaEnhanceProperties enhanceProperties;

    public KafkaConsumerTemplate(KafkaConnectionProperties properties, KafkaEnhanceProperties enhanceProperties) {
        this.properties = properties;
        this.enhanceProperties = enhanceProperties;
    }

    /**
     * 默认消费者配置
     *
     * @return 消费者配置
     */
    public Map<String, Object> defaultConsumerConfigs() {
        // 使用 KafkaProperties 的 buildConsumerProperties 方法创建 KafkaConsumer 的配置参数
        Map<String, Object> propsMap = new HashMap<>(this.properties.buildConsumerProperties());

        // 基础配置
        propsMap.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.TRUE);
        propsMap.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 1000);

        // 连接配置
        propsMap.putIfAbsent(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, 3000);      // 重连间隔，默认50ms
        propsMap.putIfAbsent(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, 10000); // 最大重连间隔，默认1000ms
        propsMap.putIfAbsent(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, 3000);          // 重试间隔，默认100ms
        propsMap.putIfAbsent(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);       // 请求超时时间，默认30s
        propsMap.putIfAbsent(ConsumerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 540000); // 连接最大空闲时间，默认9分钟

        // 心跳和会话超时配置
        propsMap.putIfAbsent(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 60000);       // 会话超时时间，默认30s, 设置为 60s, 必须大于心跳间隔
        propsMap.putIfAbsent(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 15000);    // 心跳间隔时间，默认1s, 设置为 15s
        propsMap.putIfAbsent(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 600000);    // 最大拉取间隔，10分钟

        // 反序列化配置
        propsMap.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        propsMap.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // 消费者性能调优
        propsMap.putIfAbsent(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
        propsMap.putIfAbsent(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        propsMap.putIfAbsent(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 52428800);       // 50MB
        propsMap.putIfAbsent(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 5000);
        propsMap.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // 压缩配置
        // 压缩可以减少数据的大小，从而提高网络传输效率和节省存储空间。
        // 常见的压缩类型：
        // none：不压缩（默认选项）。
        // gzip：使用 GZIP 压缩，压缩率高，但会占用较多 CPU 资源。
        //  snappy：使用 Snappy 压缩，速度快，CPU 占用较少，但压缩率不如 GZIP。
        //  lz4：使用 LZ4 压缩，速度非常快，压缩率和速度之间有一个很好的平衡。
        //  zstd：使用 Zstandard 压缩，压缩率高，速度也较快。
        propsMap.putIfAbsent(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // 信任包配置
        propsMap.putIfAbsent("spring.json.trusted.packages", "*");

        // 移除客户端ID，避免客户端ID相同导致的问题
        propsMap.remove(ConsumerConfig.CLIENT_ID_CONFIG);
        return propsMap;
    }

    /**
     * 创建普通消费者配置
     *
     * @param groupId 消费组ID
     * @return 消费者配置
     */
    public Map<String, Object> createConsumerConfigs(String groupId) {
        Map<String, Object> propsMap = new HashMap<>(defaultConsumerConfigs());
        // 设置消费组ID
        propsMap.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        return propsMap;
    }

    /**
     * 创建事务消费者配置
     *
     * @return 事务消费者配置
     */
    public Map<String, Object> defaultTransactionConsumerConfigs() {
        Map<String, Object> propsMap = new HashMap<>(defaultConsumerConfigs());
        // 关闭自动提交偏移量，设置 consumer 手动提交
        propsMap.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // 当各分区下有已提交的offset时，从提交的offset开始消费；无提交的offset时，从头开始消费
        propsMap.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // 事务隔离级别，read_committed 读取不属于事务和事务提交后的消息||read_uncommitted 所用消息
        propsMap.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        return propsMap;
    }

    //============================ 普通消费者创建方法 ============================

    /**
     * 创建指定消费组、主题和偏移量的普通消费者
     *
     * @param groupId 消费组ID
     * @return 普通消费者
     */
    public KafkaConsumer<String, String> createConsumer(String groupId) {
        Map<String, Object> propsMap = this.createConsumerConfigs(groupId);
        return new KafkaConsumer<>(propsMap);
    }

    //============================ 事务消费者创建方法 ============================

    /**
     * 创建事务消费者
     *
     * @param groupId 消费组ID
     * @return 事务消费者
     */
    public KafkaConsumer<String, String> createTransactionConsumer(String groupId) {
        Map<String, Object> propsMap = this.defaultTransactionConsumerConfigs();
        propsMap.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        return new KafkaConsumer<>(propsMap);
    }

    /**
     * 为消费者分配主题分区和偏移量
     *
     * @param consumer   消费者
     * @param topic      主题
     * @param fromOffset 起始偏移量
     */
    public void assignTopicPartition(KafkaConsumer<String, String> consumer, String topic, long fromOffset) {
        TopicPartition topicPartition = new TopicPartition(topic, 0);
        consumer.assign(Collections.singletonList(topicPartition));
    }

    //============================ 消费者工厂方法 ============================

    /**
     * 创建普通消费者工厂
     *
     * @return 消费者工厂
     */
    public DefaultKafkaConsumerFactory<String, String> createConsumerFactory() {
        Map<String, Object> propsMap = defaultConsumerConfigs();
        // 移除客户端ID，避免客户端ID相同导致的问题
        propsMap.remove(ConsumerConfig.CLIENT_ID_CONFIG);
        return createConsumerFactory(propsMap);
    }

    /**
     * 创建事务消息消费者工厂
     *
     * @return 消费者工厂
     */
    public DefaultKafkaConsumerFactory<String, String> createTransactionConsumerFactory() {
        Map<String, Object> propsMap = defaultTransactionConsumerConfigs();
        // 如果两次poll操作间隔超过了这个时间，broker就会认为这个consumer处理能力太弱，会将其踢出消费组，将分区分配给别的consumer消费 ，触发 rebalance
        propsMap.putIfAbsent(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 600000); // 10分钟
        propsMap.putIfAbsent(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
        propsMap.putIfAbsent(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        propsMap.putIfAbsent(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 5000); // 5s
        return createConsumerFactory(propsMap);
    }

    /**
     * 创建消费者工厂
     *
     * @param propsMap 配置参数
     * @return 消费者工厂
     */
    public DefaultKafkaConsumerFactory<String, String> createConsumerFactory(Map<String, Object> propsMap) {
        // 移除客户端ID，避免客户端ID相同导致的问题
        propsMap.remove(ConsumerConfig.CLIENT_ID_CONFIG);
        return new DefaultKafkaConsumerFactory<>(propsMap);
    }

    /**
     * 创建Kafka监听容器工厂（普通消息）
     *
     * @return Kafka监听容器工厂
     */
    public ConcurrentKafkaListenerContainerFactory<String, String> createKafkaListenerContainerFactory() {
        return createKafkaListenerContainerFactory(createConsumerFactory());
    }

    /**
     * 创建Kafka监听容器工厂（事务消息）
     *
     * @return Kafka监听容器工厂
     */
    public ConcurrentKafkaListenerContainerFactory<String, String> createkafkaTsListenerContainerFactory() {
        return this.createKafkaListenerContainerFactory(createTransactionConsumerFactory());
    }

    /**
     * 创建Kafka监听容器工厂
     *
     * @param propsMap 配置参数
     * @return Kafka监听容器工厂
     */
    public ConcurrentKafkaListenerContainerFactory<String, String> createKafkaListenerContainerFactory(Map<String, Object> propsMap) {
        return createKafkaListenerContainerFactory(createConsumerFactory(propsMap));
    }

    /**
     * 创建Kafka监听容器工厂
     *
     * @param consumerFactory 消费者工厂
     * @return Kafka监听容器工厂
     */
    public ConcurrentKafkaListenerContainerFactory<String, String> createKafkaListenerContainerFactory(ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();

        // 设置消费者工厂
        factory.setConsumerFactory(consumerFactory);

        // 默认的批量监听配置
        factory.setBatchListener(true);
       /* factory.setBatchErrorHandler((thrownException, data) -> {
            log.error("Batch error handler caught exception: {}", thrownException.getMessage(), thrownException);
        });*/

        // 消息过滤
        factory.setRecordFilterStrategy(consumerRecord -> consumerRecord.value() == null);

        return factory;
    }


    //============================ 消费者获取方法 ============================

    /**
     * 获取指定主题和偏移量的普通消费者
     *
     * @param topic 主题
     * @return 普通消费者
     */
    public KafkaConsumer<String, String> getConsumer(String topic, String groupId) {
        if (StringUtils.isBlank(topic)) {
            throw new IllegalArgumentException("topic 不能为空");
        }
        KafkaConsumer<String, String> consumer = createConsumer(groupId);
        consumer.subscribe(Collections.singleton(topic));
        log.info("Create Kafka Consumer For Topic: {}, GroupId: {}", topic, groupId);
        return consumer;
    }

    /**
     * 获取指定消费组、主题和偏移量的普通消费者
     *
     * @param topic      主题
     * @param groupId    消费组ID
     * @param fromOffset 起始偏移量
     * @return 普通消费者
     */
    public KafkaConsumer<String, String> getConsumer(String topic, String groupId, long fromOffset) {
        if (StringUtils.isBlank(topic)) {
            throw new IllegalArgumentException("topic 不能为空");
        }
        KafkaConsumer<String, String> consumer = createConsumer(groupId);
        assignTopicPartition(consumer, topic, fromOffset);
        log.info("Create Kafka Consumer For Topic: {}, GroupId: {}, FromOffset: {}", topic, groupId, fromOffset);
        return consumer;
    }

    /**
     * 获取事务消费者
     *
     * @param topic   主题
     * @param groupId 消费组ID
     * @return 事务消费者
     */
    public KafkaConsumer<String, String> getTransactionConsumer(String topic, String groupId) {
        if (StringUtils.isBlank(topic)) {
            throw new IllegalArgumentException("topic 不能为空");
        }
        KafkaConsumer<String, String> consumer = createTransactionConsumer(groupId);
        consumer.subscribe(Collections.singleton(topic));
        log.info("Create Kafka Transaction Consumer For Topic: {}, GroupId: {}", topic, groupId);
        return consumer;
    }

    /**
     * 获取指定主题和偏移量的事务消费者
     *
     * @param topic      主题
     * @param groupId    消费组ID
     * @param fromOffset 起始偏移量
     * @return 事务消费者
     */
    public KafkaConsumer<String, String> getTransactionConsumer(String topic, String groupId, long fromOffset) {
        if (StringUtils.isBlank(topic)) {
            throw new IllegalArgumentException("topic 不能为空");
        }
        KafkaConsumer<String, String> consumer = createTransactionConsumer(groupId);
        assignTopicPartition(consumer, topic, fromOffset);
        log.info("Create Kafka Transaction Consumer For Topic: {}, GroupId: {}, FromOffset: {}", topic, groupId, fromOffset);
        return consumer;
    }

    public <K, V> Map<TopicPartition, ConsumerRecord<K, V>> getHighestOffsetRecords(List<ConsumerRecord<K, V>> records) {
        Map<TopicPartition, ConsumerRecord<K, V>> highestOffsetMap = new HashMap<>();
        for (ConsumerRecord<K, V> record : records) {
            TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
            ConsumerRecord<K, V> consumerRecord = highestOffsetMap.get(topicPartition);
            if (consumerRecord == null || record.offset() > consumerRecord.offset()) {
                highestOffsetMap.put(topicPartition, record);
            }
        }
        return highestOffsetMap;
    }

    /*public ErrorHandler createErrorHandler(KafkaTemplate<String, String> kafkaTemplate){
        KafkaEnhanceProperties.EnhanceListener listener = enhanceProperties.getListener();
        switch (listener.getErrorHandlerMode()) {
            case SEEK_TO_CURRENT:
                if (KafkaEnhanceProperties.BackOffMode.Fixed.equals(listener.getBackOffMode())) {
                    ExponentialBackOff backOff = new ExponentialBackOff();
                    backOff.setInitialInterval(listener.getBackOffInitialInterval()); // 初始间隔 1 秒
                    backOff.setMultiplier(listener.getBackOffMultiplier()); // 每次重试间隔加倍
                    backOff.setMaxInterval(listener.getBackOffMaxInterval()); // 最大间隔 60 秒
                    backOff.setMaxElapsedTime(listener.getBackOffMaxElapsedTime()); // 最大重试时间 5 分钟
                    return new SeekToCurrentErrorHandler(backOff);
                }
                return new SeekToCurrentErrorHandler(new FixedBackOff(listener.getBackOffInterval(), listener.getBackOffMaxAttempts()));
            case SEEK_TO_CURRENT_WITH_DEAD_LETTER_QUEUE:
                DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
                if (KafkaEnhanceProperties.BackOffMode.Fixed.equals(listener.getBackOffMode())) {
                    ExponentialBackOff backOff = new ExponentialBackOff();
                    backOff.setInitialInterval(listener.getBackOffInitialInterval()); // 初始间隔 1 秒
                    backOff.setMultiplier(listener.getBackOffMultiplier()); // 每次重试间隔加倍
                    backOff.setMaxInterval(listener.getBackOffMaxInterval()); // 最大间隔 60 秒
                    backOff.setMaxElapsedTime(listener.getBackOffMaxElapsedTime()); // 最大重试时间 5 分钟
                    return new SeekToCurrentErrorHandler(recoverer, backOff);
                }
                FixedBackOff backOff = new FixedBackOff(listener.getBackOffInterval(), listener.getBackOffMaxAttempts());
                return new SeekToCurrentErrorHandler(recoverer, backOff);
            default:
                return new LoggingErrorHandler();
        }
    }

    public BatchErrorHandler createBatchErrorHandler(KafkaTemplate<String, String> kafkaTemplate){
        KafkaEnhanceProperties.EnhanceListener listener = enhanceProperties.getListener();
        switch (listener.getBatchErrorHandlerMode()) {
            case SEEK_TO_CURRENT:
                SeekToCurrentBatchErrorHandler batchErrorHandler = new SeekToCurrentBatchErrorHandler();
                batchErrorHandler.setAckAfterHandle(listener.isAckAfterHandle());
                if (KafkaEnhanceProperties.BackOffMode.Fixed.equals(listener.getBackOffMode())) {
                    ExponentialBackOff backOff = new ExponentialBackOff();
                    backOff.setInitialInterval(listener.getBackOffInitialInterval()); // 初始间隔 1 秒
                    backOff.setMultiplier(listener.getBackOffMultiplier()); // 每次重试间隔加倍
                    backOff.setMaxInterval(listener.getBackOffMaxInterval()); // 最大间隔 60 秒
                    backOff.setMaxElapsedTime(listener.getBackOffMaxElapsedTime()); // 最大重试时间 5 分钟
                    batchErrorHandler.setBackOff(backOff);
                    return batchErrorHandler;
                }
                FixedBackOff backOff = new FixedBackOff(listener.getBackOffInterval(), listener.getBackOffMaxAttempts());
                batchErrorHandler.setBackOff(backOff);
                return batchErrorHandler;
            default:
                return new BatchLoggingErrorHandler();
        }
    }*/

    //============================ 资源管理方法 ============================

    /**
     * 清理指定消费者
     *
     * @param topic 消费主题
     */
    public void closeConsumer(String topic) {
        KafkaConsumer<String, String> wrapper = CONSUMER_MAP.remove(topic);
        if (wrapper != null) {
            wrapper.close();
        }
    }

    /**
     * 获取活跃消费者数量
     *
     * @return 活跃消费者数量
     */
    public int getActiveConsumerCount() {
        return CONSUMER_MAP.size();
    }

    /**
     * 获取活跃消费者ID
     *
     * @return 活跃消费者ID数组
     */
    public String[] getActiveConsumerIds() {
        return CONSUMER_MAP.keySet().toArray(new String[0]);
    }

    /**
     * 销毁资源
     */
    @Override
    public void destroy() {
        log.info("关闭所有Kafka消费者...");

        // 关闭普通消费者
        for (Map.Entry<String, KafkaConsumer<String, String>> entry : CONSUMER_MAP.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                log.error("关闭消费者[{}]异常: {}", entry.getKey(), e.getMessage(), e);
            }
        }
        CONSUMER_MAP.clear();
        log.info("Kafka消费者资源已释放");
    }

    // 检查系统内存状态
    private boolean isLowMemory() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double memoryUsageRatio = (double) usedMemory / maxMemory;

        // 如果内存使用率超过85%，认为内存不足
        return memoryUsageRatio > 0.85;
    }

}

