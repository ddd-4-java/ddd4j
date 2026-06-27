package io.ddd4j.mq.kafka.consumer;

import io.ddd4j.mq.kafka.config.KafkaConnectionProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Slf4j
public class KafkaProducerTemplate implements DisposableBean {

    private final KafkaConnectionProperties properties;

    public KafkaProducerTemplate(KafkaConnectionProperties properties) {
        this.properties = properties;
    }

    // 使用统一的 Map 存储所有生产者实例，key 格式为 "type:id"，例如 "transaction:exchange-order"
    private final Map<String, ProducerWrapper> PRODUCER_MAP = new ConcurrentHashMap<>();

    // 用于保护生产者创建和移除操作的锁
    private final Map<String, Lock> PRODUCER_LOCKS = new ConcurrentHashMap<>();

    public final static String TRANSACTION_ID_PREFIX = "tx-";

    /**
     * KafkaProducer 的配置参数（非事务消息配置）
     *
     * @return KafkaProducer 的配置参数
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
    public Map<String, Object> defaultProducerConfigs() {
        // 使用 KafkaProperties 的 buildProducerProperties 方法创建 KafkaProducer 的配置参数
        Map<String, Object> props = new HashMap<>(this.properties.buildProducerProperties());
        // key 和 value 的反序列化方式
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // 设置更大的缓冲区内存，例如 10MB (10485760 字节)
        props.putIfAbsent(ProducerConfig.BUFFER_MEMORY_CONFIG, 10485760);
        // 可能还需要增加最大请求大小
        props.putIfAbsent(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 5242880); // 5MB
        // 压缩配置
        // 压缩可以减少数据的大小，从而提高网络传输效率和节省存储空间。
        // 常见的压缩类型：
        // none：不压缩（默认选项）。
        // gzip：使用 GZIP 压缩，压缩率高，但会占用较多 CPU 资源。
        //  snappy：使用 Snappy 压缩，速度快，CPU 占用较少，但压缩率不如 GZIP。
        //  lz4：使用 LZ4 压缩，速度非常快，压缩率和速度之间有一个很好的平衡。
        //  zstd：使用 Zstandard 压缩，压缩率高，速度也较快。
        props.putIfAbsent(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // 去除与事务相关的配置
        props.remove(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
        props.remove(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG);
        props.remove(ProducerConfig.ACKS_CONFIG);
        props.remove(ProducerConfig.RETRIES_CONFIG);

        // 移除客户端ID，避免客户端ID相同导致的问题
        props.remove(ProducerConfig.CLIENT_ID_CONFIG);
        return props;
    }

    /**
     * KafkaProducer 的配置参数（事务消息配置）
     *
     * @return KafkaProducer 的配置参数
     */
    public Map<String, Object> createTransactionProducerConfigs() {
        // 使用 KafkaProperties 的 buildProducerProperties 方法创建 KafkaProducer 的配置参数
        Map<String, Object> props = new HashMap<>(this.properties.buildProducerProperties());
        // 设置更大的缓冲区内存，例如 10MB (10485760 字节)
        props.putIfAbsent(ProducerConfig.BUFFER_MEMORY_CONFIG, 10485760);
        // 可能还需要增加最大请求大小
        props.putIfAbsent(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 5242880); // 5MB
        // key 和 value 的反序列化方式
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // 事务ID，如果未指定，则使用随机生成的 UUID
        props.putIfAbsent(ProducerConfig.TRANSACTIONAL_ID_CONFIG, TRANSACTION_ID_PREFIX + UUID.randomUUID());
        // 启用幂等性，保证消息不会重复发送（幂等性：在幂等性模式下，Kafka 会确保消息只被处理一次，不会重复处理）
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // 要求ISR都确认，Leader 接收 0; Leader写入 1; 所有副本写入 all;
        // acks=0 ： 生产者在成功写入消息之前不会等待任何来自服务器的响应。
        // acks=1 ： 只要集群的首领节点收到消息，生产者就会收到一个来自服务器成功响应。
        // acks=all ：只有当所有参与复制的节点全部收到消息时，生产者才会收到一个来自服务器的成功响应。
        // # 开启事务时，必须设置为all
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        // 重试次数，发生错误后，消息重发的次数，开启事务必须设置大于0
        props.put(ProducerConfig.RETRIES_CONFIG, Math.max(MapUtils.getIntValue(props, ProducerConfig.RETRIES_CONFIG, 0), 3));

        // 移除客户端ID，避免客户端ID相同导致的问题
        props.remove(ProducerConfig.CLIENT_ID_CONFIG);
        return props;
    }

    public String getDefaultTopic() {
        return this.properties.getDefaultTopic();
    }

    /**
     * 创建 ProducerFactory 的 Bean （非事务消息）
     *
     * @return ProducerFactory 是 Spring Kafka 提供的一个工厂类，用于创建 KafkaProducer 对象
     */
    public DefaultKafkaProducerFactory<String, String> createProducerFactory() {

        // 创建非事务消息的配置参数
        Map<String, Object> propsMap = defaultProducerConfigs();

        // 创建 KafkaProducer 的工厂类
        return new DefaultKafkaProducerFactory<>(propsMap);
    }

    /**
     * 创建 ProducerFactory 的 Bean （事务消息）
     *
     * @return ProducerFactory 是 Spring Kafka 提供的一个工厂类，用于创建 KafkaProducer 对象
     */
    public DefaultKafkaProducerFactory<String, String> createTransactionProducerFactory() {

        // 创建事务消息的配置参数
        Map<String, Object> propsMap = createTransactionProducerConfigs();

        // 创建 KafkaProducer 的工厂类      
        DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(propsMap);

        // 设置事务ID前缀
        String transactionIdPrefix = this.properties.getTransactionIdPrefix();
        if (transactionIdPrefix != null) {
            factory.setTransactionIdPrefix(transactionIdPrefix);
        } else {
            factory.setTransactionIdPrefix(TRANSACTION_ID_PREFIX + UUID.randomUUID());
        }
        return factory;
    }

    /**
     * 获取普通 KafkaProducer 对象，如果不存在则创建
     *
     * @param topic 主题
     * @return 普通 KafkaProducer 对象
     */
    public ProducerWrapper getProducer(String topic) {
        // 如果 topic 为空，则抛出异常
        if (StringUtils.isBlank(topic)) {
            throw new IllegalArgumentException("topic 不能为空");
        }
        // 创建普通生产者的 key
        String key = "normal:" + topic;
        return getOrCreateProducer(key, () -> {
            // 创建普通生产者的配置参数
            Map<String, Object> propsMap = new HashMap<>(defaultProducerConfigs());
            log.info("创建普通生产者: {}", topic);
            return new KafkaProducer<>(propsMap);
        });
    }

    /**
     * 获取或创建事务生产者
     *
     * @param topic 主题
     * @return 事务 KafkaProducer 对象
     */
    public ProducerWrapper getTransactionProducer(String topic) {
        // 如果 topic 为空，则抛出异常
        if (StringUtils.isBlank(topic)) {
            throw new IllegalArgumentException("topic 不能为空");
        }
        // 创建事务生产者的 key
        String key = "transaction:" + topic;
        return getOrCreateProducer(key, () -> {
            // 创建事务消息的配置参数
            Map<String, Object> props = createTransactionProducerConfigs();
            // 创建 KafkaProducer 对象
            KafkaProducer<String, String> producer = new KafkaProducer<>(props);
            try {
                // 初始化事务
                producer.initTransactions();
                log.info("创建事务生产者: {}, 事务ID: {}", key, props.get(ProducerConfig.TRANSACTIONAL_ID_CONFIG));
                return producer;
            } catch (Exception e) {
                log.error("初始化事务生产者失败: {}", e.getMessage(), e);
                producer.close();
                throw e;
            }
        });
    }

    /**
     * 获取或创建生产者，使用锁保证线程安全
     *
     * @param topic   主题
     * @param factory 生产者工厂函数
     * @return KafkaProducer 实例
     */
    private ProducerWrapper getOrCreateProducer(String topic, Supplier<KafkaProducer<String, String>> factory) {
        // 先尝试从缓存获取
        ProducerWrapper wrapper = PRODUCER_MAP.get(topic);
        if (wrapper != null) {
            return wrapper;
        }

        // 获取或创建锁
        Lock lock = PRODUCER_LOCKS.computeIfAbsent(topic, k -> new ReentrantLock());
        boolean locked = false;
        try {
            // 尝试获取锁，最多等待5秒
            locked = lock.tryLock(5, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("获取锁超时，可能存在死锁风险: {}", topic);
                throw new RuntimeException("获取生产者锁超时，请稍后重试");
            }

            // 再次检查，防止在获取锁的过程中其他线程已经创建了生产者
            wrapper = PRODUCER_MAP.get(topic);
            if (wrapper != null) {
                return wrapper;
            }

            // 创建新的生产者
            KafkaProducer<String, String> producer = factory.get();
            wrapper = new ProducerWrapper(producer);
            PRODUCER_MAP.put(topic, wrapper);
            return wrapper;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取生产者被中断", e);
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    /**
     * 异步发送消息（非事务性）
     *
     * @param topic    主题
     * @param value    消息值
     * @param callback 回调函数，可为null
     */
    public void send(String topic, String value, Callback callback) {
        if (StringUtils.isBlank(topic)) {
            throw new IllegalArgumentException("topic 不能为空");
        }
        KafkaProducer<String, String> producer = getProducer(topic).getProducer();
        producer.send(new ProducerRecord<>(topic, value), callback);
        log.debug("异步发送消息: topic={}, value={}", topic, value);
    }

    /**
     * 异步发送消息（非事务性），无回调
     *
     * @param topic 主题
     * @param value 消息值
     */
    public void send(String topic, String value) {
        if (StringUtils.isBlank(topic)) {
            throw new IllegalArgumentException("topic 不能为空");
        }
        KafkaProducer<String, String> producer = getProducer(topic).getProducer();
        producer.send(new ProducerRecord<>(topic, value));
        log.debug("异步发送消息: topic={}, value={}", topic, value);
    }

    /**
     * 批量异步发送消息（非事务性）
     *
     * @param topic    主题
     * @param messages 消息列表，每个元素是一个Map，包含key和value
     * @param callback 回调函数，可为null
     */
    public void send(String topic, List<Map<String, String>> messages, Callback callback) {
        if (StringUtils.isBlank(topic)) {
            throw new IllegalArgumentException("topic 不能为空");
        }
        if (CollectionUtils.isEmpty(messages)) {
            log.warn("消息列表为空，无需发送");
            return;
        }

        KafkaProducer<String, String> producer = getProducer(topic).getProducer();

        for (Map<String, String> message : messages) {
            String key = message.get("key");
            String value = message.get("value");
            producer.send(new ProducerRecord<>(topic, key, value), callback);
        }

        log.debug("批量异步发送消息: topic={}, 消息数量={}", topic, messages.size());
    }

    /**
     * 批量异步发送消息（非事务性），无回调
     *
     * @param topic    主题
     * @param messages 消息列表，每个元素是一个Map，包含key和value
     */
    public void send(String topic, List<Map<String, String>> messages) {
        if (StringUtils.isBlank(topic)) {
            throw new IllegalArgumentException("topic 不能为空");
        }
        if (CollectionUtils.isEmpty(messages)) {
            log.warn("消息列表为空，无需发送");
            return;
        }

        KafkaProducer<String, String> producer = getProducer(topic).getProducer();

        for (Map<String, String> message : messages) {
            String key = message.get("key");
            String value = message.get("value");
            producer.send(new ProducerRecord<>(topic, key, value));
        }

        log.debug("批量异步发送消息: topic={}, 消息数量={}", topic, messages.size());
    }

    /**
     * 应用关闭时清理所有生产者资源
     */
    @Override
    public void destroy() {
        log.info("开始关闭所有Kafka生产者...");

        // 创建一个副本以避免并发修改异常
        Map<String, ProducerWrapper> producersCopy = new HashMap<>(PRODUCER_MAP);

        producersCopy.forEach((key, wrapper) -> {
            try {
                if (wrapper.getProducer() != null) {
                    wrapper.getProducer().close(Duration.ofSeconds(30));
                    log.info("关闭生产者: {}", key);
                }
            } catch (Exception e) {
                log.error("关闭生产者失败: {}, 错误: {}", key, e.getMessage(), e);
            }
        });

        PRODUCER_MAP.clear();
        PRODUCER_LOCKS.clear();
        log.info("所有Kafka生产者已关闭");
    }

    /**
     * 获取当前活跃的生产者数量
     *
     * @return 活跃的生产者数量
     */
    public int getActiveProducerCount() {
        return PRODUCER_MAP.size();
    }

    /**
     * 获取当前活跃的生产者键列表
     *
     * @return 活跃的生产者键列表
     */
    public String[] getActiveProducerKeys() {
        return PRODUCER_MAP.keySet().toArray(new String[0]);
    }

}

