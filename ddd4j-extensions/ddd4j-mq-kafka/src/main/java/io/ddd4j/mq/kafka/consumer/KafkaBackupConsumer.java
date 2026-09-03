package io.ddd4j.mq.kafka.consumer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.google.common.collect.Lists;
import io.ddd4j.mq.kafka.KafkaBackupProperties;
import io.ddd4j.mq.kafka.consumer.backup.BackupRecord;
import io.ddd4j.mq.kafka.consumer.backup.BackupStatus;
import io.ddd4j.core.exception.BizRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.DisposableBean;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Kafka备份消费者，提供事务消息消费和本地备份功能
 * 确保在消费前先进行备份，消费成功后删除备份
 */
@Slf4j
public class KafkaBackupConsumer implements DisposableBean {

    private final KafkaConsumerTemplate kafkaConsumerTemplate;
    private final String topic;
    private final String consumerGroupId;
    private final BiConsumer<Integer, ConsumerRecord<String, String>> recordConsumer;

    // 消费者线程池
    private final ExecutorService consumerExecutor;

    // 备份清理线程池
    private final ScheduledExecutorService cleanupExecutor;

    // 控制消费者运行状态
    private final AtomicBoolean running = new AtomicBoolean(true);

    // 备份操作的信号量，限制并发备份数量
    private final Semaphore backupSemaphore = new Semaphore(1);

    // 记录最后成功提交的偏移量，key 为 "topic:partition"
    private final Map<String, Long> lastCommittedOffsetMap = new ConcurrentHashMap<>();

    // 备份状态映射，key 为 "topic:partition"
    private final Map<String, BackupStatus> backupStatusMap = new ConcurrentHashMap<>();

    // 最后一次备份的偏移量映射，key 为 "topic:partition"
    private final Map<String, Long> lastBackupOffsetMap = new ConcurrentHashMap<>();

    private final KafkaBackupProperties backupProperties;

    /**
     * 构造函数
     *
     * @param kafkaConsumerTemplate 消费者模板
     * @param topic                 执行主题
     * @param consumerGroupId       消费者组ID
     * @param consumer              消息处理器
     */
    public KafkaBackupConsumer(KafkaConsumerTemplate kafkaConsumerTemplate,
                               KafkaBackupProperties backupProperties,
                               String topic,
                               String consumerGroupId,
                               BiConsumer<Integer, ConsumerRecord<String, String>> consumer) {
        this.kafkaConsumerTemplate = kafkaConsumerTemplate;
        this.backupProperties = backupProperties;
        this.topic = topic;
        this.consumerGroupId = consumerGroupId;
        this.recordConsumer = consumer;

        // 创建线程池，使用有界队列避免任务堆积
        this.consumerExecutor = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                r -> {
                    Thread t = new Thread(r, "backup-consumer-" + topic);
                    t.setDaemon(true); // 设置为守护线程
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者运行
        );

        // 创建备份清理线程池
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "backup-cleanup-" + topic);
            t.setDaemon(true);
            return t;
        });

        // 加载备份索引
        loadBackupIndex();

        // 开启线程执行
        init();

        // 启动备份清理任务
        startBackupCleanupTask();

        // 启动备份索引保存任务
        startIndexSaveTask();

        log.info("KafkaBackupConsumer 已初始化: topic={}, groupId={}", topic, consumerGroupId);
    }


    private String getPartitionKey(String topic, int partition) {
        return topic + ":" + partition;
    }

    private String getIndexFilePath(String topic, int partition) {
        ensureBackupDir();
        return backupProperties.getBackupDir() + File.separator + topic + "-" + partition + "-index.json";
    }

    private String getBackupFilePath(String topic, int partition) {
        ensureBackupDir();
        return backupProperties.getBackupDir() + File.separator + topic + "-" + partition + "-backup.txt";
    }

    /**
     * 获取备份消费偏移量
     *
     * @param topic      主题
     * @param partition  分区
     * @param loadOffset 加载偏移量
     * @return 备份消费偏移量
     */
    public long getBackupConsumedOffset(String topic, int partition, long loadOffset) {
        try {
            BackupRecord backupRecord = getBackupRecord(topic, partition);
            if (Objects.nonNull(backupRecord)) {
                return backupRecord.getLastConsumedOffset();
            }
        } catch (IOException e) {
            log.error("获取备份消费偏移量异常：{}", ExceptionUtils.getStackTrace(e));
        }
        return loadOffset;
    }

    private void ensureBackupDir() {
        // 创建备份目录
        String backupDir = backupProperties.getBackupDir();
        if (StringUtils.isBlank(backupDir)) {
            backupDir = System.getProperty("user.dir") + File.separator + "backup" + File.separator;
            backupProperties.setBackupDir(backupDir);
        }
        File backupDirFile = new File(backupDir);
        if (!backupDirFile.exists()) {
            try {
                FileUtils.forceMkdir(backupDirFile);
            } catch (IOException e) {
                log.error("创建备份目录失败: {}", backupDir, e);
            }
        }
    }

    /**
     * 加载备份索引
     */
    private void loadBackupIndex() {
        try {

            // 创建备份目录
            ensureBackupDir();

            // 查找备份索引文件
            File[] files = new File(backupProperties.getBackupDir()).listFiles(file ->
                    file.getName().startsWith(topic) && file.getName().endsWith("-index.json"));

            if (ArrayUtils.isEmpty(files)) {

                // 查找备份索引文件
                String indexFilePath = getIndexFilePath(topic, 0);
                File indexFile = new File(indexFilePath);

                files = new File[]{indexFile};
            }

            for (File indexFile : files) {

                // 如果索引文件存在，则加载
                if (indexFile.exists()) {

                    // topic + "-" + partition + "-index.json"， 使用正则表达式获取分区号
                    String[] parts = indexFile.getName().split("-");

                    // 读取索引文件内容
                    String jsonContent = FileUtils.readFileToString(indexFile, StandardCharsets.UTF_8);
                    Map<String, Object> indexData = JSON.parseObject(jsonContent, new TypeReference<Map<String, Object>>() {
                    });

                    // 加载最后提交的偏移量
                    String partitionKey = getPartitionKey(parts[0], Integer.parseInt(parts[1]));
                    Object lastCommittedOffsetObj = indexData.get("lastCommittedOffset");
                    if (lastCommittedOffsetObj != null) {
                        lastCommittedOffsetMap.put(partitionKey, Long.parseLong(lastCommittedOffsetObj.toString()));
                    }

                    // 加载最后备份的偏移量映射
                    Map<String, Long> loadedLastBackupOffsetMap = JSON.parseObject(
                            JSON.toJSONString(indexData.get("lastBackupOffsetMap")),
                            new TypeReference<Map<String, Long>>() {
                            });
                    if (loadedLastBackupOffsetMap != null) {
                        lastBackupOffsetMap.putAll(loadedLastBackupOffsetMap);
                    }

                    // 加载备份状态映射
                    Map<String, Map<String, Object>> loadedStatusMap = JSON.parseObject(
                            JSON.toJSONString(indexData.get("backupStatusMap")),
                            new TypeReference<Map<String, Map<String, Object>>>() {
                            });

                    if (loadedStatusMap != null) {
                        for (Map.Entry<String, Map<String, Object>> entry : loadedStatusMap.entrySet()) {
                            Map<String, Object> statusData = entry.getValue();
                            long offset = Long.parseLong(statusData.get("offset").toString());
                            long createTime = Long.parseLong(statusData.get("createTime").toString());
                            boolean consumed = Boolean.parseBoolean(statusData.get("consumed").toString());
                            boolean offsetCommitted = Boolean.parseBoolean(statusData.get("offsetCommitted").toString());

                            BackupStatus status = new BackupStatus(offset, createTime);
                            status.setConsumed(consumed);
                            status.setOffsetCommitted(offsetCommitted);

                            backupStatusMap.put(entry.getKey(), status);
                        }
                    }
                    log.info("加载备份索引成功: topic={}, partition={}, entries={}", parts[0], Integer.parseInt(parts[1]), backupStatusMap.size());
                }

            }

        } catch (Exception e) {
            log.error("加载备份索引失败: topic={}, exception={}", topic, e.getMessage(), e);
        }
    }

    /**
     * 初始化并启动消费
     */
    private void init() {
        consumerExecutor.submit(this::runConsumer);
    }

    /**
     * 消费者主循环
     */
    private void runConsumer() {

        // 创建消费者，订阅主题
        KafkaConsumer<String, String> consumer = kafkaConsumerTemplate.getTransactionConsumer(topic, consumerGroupId);
        TopicPartition topicPartition = new TopicPartition(topic, 0);
        consumer.assign(Collections.singletonList(topicPartition));
        // 初始化偏移量
        initializeOffset(consumer, topicPartition);

        // 业务处理变量
        Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
        List<ConsumerRecord<String, String>> recordList;
        long lastConsumedOffset = 0;

        while (running.get()) {
            try {

                offsets.clear();

                // 拉取消息
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                if (records!isPresent()) {
                    // 避免没消息的时候，空转
                    Thread.sleep(100);
                    continue;
                }

                // 按分区处理消息
                for (TopicPartition partition : records.partitions()) {
                    try {

                        // 获取当前分区的消息
                        recordList = records.records(partition);
                        int recordSize = recordList.size();

                        // 获取最后一条消息的 offset
                        lastConsumedOffset = recordList.get(recordSize - 1).offset();

                        // 消费前先备份消息
                        boolean backupSuccess = backupMessagesSynchronously(partition, lastConsumedOffset, recordList);

                        if (!backupSuccess) {
                            log.error("备份失败，跳过此批次消息处理: topic={}, partition={}, offset={}",
                                    partition.topic(), partition.partition(), lastConsumedOffset);
                            continue;
                        }

                        // 分批处理消息
                        processMessagesInBatches(partition, recordList);

                        // 设置提交偏移量
                        offsets.put(partition, new OffsetAndMetadata(lastConsumedOffset + 1));

                        // 标记备份为已消费
                        markBackupAsConsumed(partition.topic(), partition.partition());

                        // 提交偏移量
                        consumer.commitSync(offsets);
                        lastCommittedOffsetMap.put(getPartitionKey(partition.topic(), partition.partition()), lastConsumedOffset);

                        // 标记备份的偏移量已提交
                        markBackupAsCommitted(partition.topic(),
                                partition.partition(), lastConsumedOffset);

                    } catch (Exception e) {
                        handleProcessingError(partition, lastConsumedOffset, e);
                    }
                }
            } catch (WakeupException e) {
                if (!running.get()) {
                    break;
                }
            } catch (Exception e) {
                handleConsumerError(e);
            }
        }
    }

    private void markBackupAsConsumed(String topic, int partition) {
        String partitionKey = getPartitionKey(topic, partition);
        BackupStatus status = backupStatusMap.get(partitionKey);
        if (status != null) {
            status.setConsumed(true);
            saveBackupIndex(topic, partition);
        }
    }

    private void markBackupAsCommitted(String topic, int partition, long offset) {
        String partitionKey = getPartitionKey(topic, partition);
        BackupStatus status = backupStatusMap.get(partitionKey);
        if (status != null && status.getOffset() <= offset) {
            status.setOffsetCommitted(true);
            saveBackupIndex(topic, partition);
        }
    }

    /**
     * 同步备份消息
     */
    private boolean backupMessagesSynchronously(TopicPartition partition, long lastConsumedOffset,
                                                List<ConsumerRecord<String, String>> recordList) {
        boolean acquired = false;

        try {
            // 尝试获取备份信号量
            acquired = backupSemaphore.tryAcquire(backupProperties.getBackupTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.error("获取备份信号量超时: topic={}, partition={}",
                        partition.topic(), partition.partition());
                return false;
            }

            // 提取消息值
            List<Object> jsonList = recordList.stream()
                    .map(ConsumerRecord::value)
                    .collect(Collectors.toList());

            // 执行备份
            try {
                this.backupFile(partition.topic(), partition.partition(),
                        lastConsumedOffset, jsonList);
                return true;
            } catch (Exception e) {
                log.error("备份失败: topic={}, partition={}, offset={}",
                        partition.topic(), partition.partition(), lastConsumedOffset, e);

                // 备份失败，尝试重试
                for (int i = 0; i < backupProperties.getMaxRetryAttempts(); i++) {
                    try {
                        Thread.sleep(backupProperties.getRetryBackoffMs() * (long) Math.pow(2, i));
                        log.info("尝试重新备份 (尝试 {}/{}): topic={}, partition={}",
                                i + 1, backupProperties.getMaxRetryAttempts(), partition.topic(), partition.partition());

                        this.backupFile(partition.topic(), partition.partition(),
                                lastConsumedOffset, jsonList);

                        log.info("重试备份成功: topic={}, partition={}, offset={}, attempt={}/{}",
                                partition.topic(), partition.partition(), lastConsumedOffset,
                                i + 1, backupProperties.getMaxRetryAttempts());

                        return true;
                    } catch (Exception retryEx) {
                        log.error("重试备份失败 (尝试 {}/{}): topic={}, partition={}, exception={}",
                                i + 1, backupProperties.getMaxRetryAttempts(), partition.topic(), partition.partition(),
                                retryEx.getMessage());
                    }
                }
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("备份过程被中断: topic={}, partition={}",
                    partition.topic(), partition.partition());
            return false;
        } finally {
            if (acquired) {
                backupSemaphore.release();
            }
        }
    }

    public void loadBackupRecord(BackupRecord backupRecord, Function<Boolean, Boolean> beforeLoad,
                                 Function<String, Boolean> afterLoad) throws Exception {
        // 如果备份文件为空，直接返回
        if (Objects.isNull(backupRecord) || backupRecord.getRecordSize() < 1) {
            return;
        }
        // 加载备份前的操作
        beforeLoad.apply(true);
        // 加载备份记录
        loadBackupRecord(backupRecord, afterLoad);
    }

    public void loadBackupRecord(BackupRecord backupRecord, Function<String, Boolean> afterLoad)
            throws IOException {
        if (Objects.isNull(backupRecord) || backupRecord.getRecordSize() < 1) {
            return;
        }

        File backupFile = new File(backupRecord.getFilePath());
        if (!backupFile.exists()) {
            throw new IOException("备份文件不存在: " + backupRecord.getFilePath());
        }

        List<String> lines;
        if (backupProperties.isCompressionEnabled()) {
            lines = loadCompressedBackup(backupFile);
        } else {
            lines = FileUtils.readLines(backupFile, StandardCharsets.UTF_8);
        }

        if (lines.size() != backupRecord.getRecordSize()) {
            throw new IOException("备份记录数不匹配: 预期=" + backupRecord.getRecordSize()
                    + ", 实际=" + lines.size());
        }

        lines.stream()
                .filter(StringUtils::isNotBlank)
                .forEach(afterLoad::apply);
    }

    private List<String> loadCompressedBackup(File backupFile) throws IOException {
        List<String> lines = new ArrayList<>();
        try (GZIPInputStream gzipIn = new GZIPInputStream(FileUtils.openInputStream(backupFile))) {
            Scanner scanner = new Scanner(gzipIn, StandardCharsets.UTF_8.name());
            while (scanner.hasNextLine()) {
                lines.add(scanner.nextLine());
            }
        }
        return lines;
    }


    public boolean hasBackup(String topic, int partition) {
        if (StringUtils.isBlank(backupProperties.getBackupDir())) {
            return false;
        }
        File backupFile = new File(getBackupFilePath(topic, partition));
        return backupFile.exists();
    }

    /**
     * 获取备份记录
     *
     * @param topic     主题
     * @param partition 分区
     * @return 备份记录
     * @throws IOException IO异常
     */
    public BackupRecord getBackupRecord(String topic, int partition) throws IOException {

        // 检查备份目录
        String backupDir = backupProperties.getBackupDir();
        if (!new File(backupDir).exists()) {
            return null;
        }

        // 获取备份文件路径
        String backupFilePath = getBackupFilePath(topic, partition);
        File backupFile = new File(backupFilePath);
        if (!backupFile.exists()) {
            return null;
        }

        // 获取分区键
        String partitionKey = getPartitionKey(topic, partition);

        // 构造备份记录
        BackupRecord record = new BackupRecord();
        record.setTopic(topic);
        record.setFilePath(backupFile.getAbsolutePath());

        // 从备份状态中获取信息
        BackupStatus status = backupStatusMap.get(partitionKey);
        if (status != null) {
            record.setLastConsumedOffset(status.getOffset());
            record.setBackupTime(status.getCreateTime());

            try {
                // 获取实际记录数
                if (backupProperties.isCompressionEnabled()) {
                    record.setRecordSize(calculateRecordSize(backupFile));
                } else {
                    List<String> lines = FileUtils.readLines(backupFile, StandardCharsets.UTF_8);
                    record.setRecordSize(lines.size());
                }
            } catch (IOException e) {
                log.error("计算备份记录数失败: topic={}, partition={}, error={}",
                        topic, partition, e.getMessage());
                throw e;
            }

            log.debug("获取备份记录成功: topic={}, partition={}, offset={}, records={}, consumed={}, committed={}",
                    topic, partition, status.getOffset(), record.getRecordSize(),
                    status.isConsumed(), status.isOffsetCommitted());

            return record;
        }

        // 如果没有找到状态信息，尝试从最后备份的偏移量映射中获取
        Long lastOffset = lastBackupOffsetMap.get(partitionKey);
        if (lastOffset != null) {
            record.setLastConsumedOffset(lastOffset);
            record.setBackupTime(backupFile.lastModified());
            record.setRecordSize(calculateRecordSize(backupFile));

            log.warn("仅从偏移量映射获取到备份记录: topic={}, partition={}, offset={}, records={}",
                    topic, partition, lastOffset, record.getRecordSize());

            return record;
        }

        log.warn("未找到备份状态信息: topic={}, partition={}", topic, partition);
        return null;
    }

    /**
     * 计算备份文件中的记录数
     */
    private int calculateRecordSize(File backupFile) throws IOException {
        if (backupProperties.isCompressionEnabled()) {
            try (GZIPInputStream gzipIn = new GZIPInputStream(FileUtils.openInputStream(backupFile))) {
                Scanner scanner = new Scanner(gzipIn, StandardCharsets.UTF_8.name());
                int count = 0;
                while (scanner.hasNextLine()) {
                    scanner.nextLine();
                    count++;
                }
                return count;
            }
        } else {
            return FileUtils.readLines(backupFile, StandardCharsets.UTF_8).size();
        }
    }


    private void saveCompressedBackup(File backupFile, List<Object> jsonList) throws IOException {
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(FileUtils.openOutputStream(backupFile))) {
            for (Object json : jsonList) {
                gzipOut.write(JSON.toJSONString(json).getBytes(StandardCharsets.UTF_8));
                gzipOut.write('\n');
            }
        }
    }

    public void backupFile(String topic, int partition, long lastConsumedOffset,
                           List<Object> jsonList) throws IOException {

        ensureBackupDir();

        String partitionKey = getPartitionKey(topic, partition);

        // 检查是否需要创建新的备份
        Long lastBackupOffset = lastBackupOffsetMap.get(partitionKey);
        if (lastBackupOffset != null && lastBackupOffset >= lastConsumedOffset) {
            log.debug("跳过备份，已有更新的备份: topic={}, partition={}, currentOffset={}, lastBackupOffset={}",
                    topic, partition, lastConsumedOffset, lastBackupOffset);
            return;
        }

        // 构造备份记录
        BackupRecord record = new BackupRecord();
        record.setTopic(topic);
        record.setLastConsumedOffset(lastConsumedOffset);
        record.setRecordSize(jsonList.size());
        record.setBackupTime(System.currentTimeMillis());

        // 保存备份文件
        String backupFilePath = getBackupFilePath(topic, partition);
        File backupFile = new File(backupFilePath);
        record.setFilePath(backupFile.getAbsolutePath());

        try {
            // 保存消息内容
            if (CollectionUtils.isNotEmpty(jsonList)) {
                if (backupProperties.isCompressionEnabled()) {
                    saveCompressedBackup(backupFile, jsonList);
                } else {
                    FileUtils.writeLines(backupFile, StandardCharsets.UTF_8.name(), jsonList, false);
                }
            }

            // 更新备份状态
            BackupStatus status = new BackupStatus(lastConsumedOffset, System.currentTimeMillis());
            backupStatusMap.put(partitionKey, status);
            lastBackupOffsetMap.put(partitionKey, lastConsumedOffset);

            // 保存索引
            saveBackupIndex(topic, partition);

        } catch (Exception e) {
            log.error("备份失败: topic={}, partition={}, offset={}", topic, partition, lastConsumedOffset, e);
            FileUtils.deleteQuietly(backupFile);
            throw e;
        }
    }

    /**
     * 初始化消费偏移量
     */
    private void initializeOffset(KafkaConsumer<String, String> consumer, TopicPartition topicPartition) {
        // 获取已提交的偏移量
        Map<TopicPartition, OffsetAndMetadata> committed = consumer.committed(Collections.singleton(topicPartition));
        OffsetAndMetadata metadata = committed.get(topicPartition);
        long loadOffset = metadata != null ? metadata.offset() - 1 : -1;
        String partitionKey = getPartitionKey(topicPartition.topic(), topicPartition.partition());
        // 检查是否有备份，并从合适的位置开始消费
        if (this.hasBackup(topicPartition.topic(), topicPartition.partition())) {
            try {
                // 获取备份记录
                BackupRecord backupRecord = this.getBackupRecord(topicPartition.topic(), topicPartition.partition());
                if (backupRecord != null) {
                    long backupOffset = backupRecord.getLastConsumedOffset();
                    if (backupOffset > loadOffset) {
                        // 从备份点开始消费
                        consumer.seek(topicPartition, backupOffset + 1);
                        lastCommittedOffsetMap.put(partitionKey, backupOffset);
                        log.info("从备份点开始消费: topic={}, offset={}", topicPartition.topic(), backupOffset + 1);
                        return;
                    }
                }
            } catch (Exception e) {
                log.error("获取备份记录失败: topic={}", topicPartition.topic(), e);
            }
        }

        // 如果没有备份或备份点更早，从已提交的偏移量开始消费
        if (loadOffset >= 0) {
            consumer.seek(topicPartition, loadOffset + 1);
            lastCommittedOffsetMap.put(partitionKey, loadOffset + 1);
            log.info("从已提交点开始消费: topic={}, offset={}", topicPartition.topic(), loadOffset + 1);
        } else {
            // 从头开始消费
            consumer.seekToBeginning(Collections.singleton(topicPartition));
            lastCommittedOffsetMap.put(partitionKey, 0L);
            log.info("从头开始消费: topic={}", topicPartition.topic());
        }
    }

    /**
     * 分批处理消息
     */
    private void processMessagesInBatches(TopicPartition partition, List<ConsumerRecord<String, String>> recordList) {
        List<List<ConsumerRecord<String, String>>> batchs = Lists.partition(recordList, backupProperties.getMaxBatchSize());
        for (List<ConsumerRecord<String, String>> batch : batchs) {
            try {
                for (ConsumerRecord<String, String> record : batch) {
                    recordConsumer.accept(recordList.size(), record);
                }
            } catch (Exception e) {
                log.error("处理消息批次异常: topic={}, fromOffset={}, toOffset={}, exception={}",
                        partition.topic(), batch.get(0).offset(), batch.get(batch.size() - 1).offset(),
                        e.getMessage(), e);
                throw e;
            }
        }
    }

    /**
     * 处理消息处理错误
     */
    private void handleProcessingError(TopicPartition partition, long offset, Exception e) {
        log.error("处理消息异常: topic={}, partition={}, offset={}, exception={}",
                partition.topic(), partition.partition(), offset, e.getMessage(), e);

        if (e instanceof RetriableException) {
            // 可重试异常，等待后继续
            try {
                Thread.sleep(backupProperties.getRetryBackoffMs());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        } else {
            // 不可重试异常，抛出自定义异常
            throw new BizRuntimeException("消息处理失败: " + e.getMessage());
        }
    }

    /**
     * 处理消费者错误
     */
    private void handleConsumerError(Exception e) {
        if (e instanceof KafkaException) {
            log.error("Kafka消费者异常: {}", e.getMessage(), e);
            try {
                Thread.sleep(backupProperties.getRetryBackoffMs());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        } else {
            log.error("消费者未知异常: {}", e.getMessage(), e);
            throw new BizRuntimeException("消费者异常: " + e.getMessage());
        }
    }

    /**
     * 关闭消费者
     */
    private void closeConsumer(KafkaConsumer<String, String> consumer) {
        if (consumer != null) {
            try {
                consumer.close();
            } catch (Exception e) {
                log.error("关闭消费者异常: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 停止消费者
     */
    public void stop() {
        running.set(false);
        // 关闭消费者
        consumerExecutor.shutdown();
        try {
            if (!consumerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                consumerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            consumerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // 关闭备份清理任务
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("KafkaBackupConsumer 已停止: topic={}", topic);
    }

    @Override
    public void destroy() {
        stop();
    }

    private Map<String, Integer> getBackupsByTopic() {
        Map<String, Integer> topicStats = new HashMap<>();
        for (String key : backupStatusMap.keySet()) {
            String topic = key.split(":")[0];
            topicStats.merge(topic, 1, Integer::sum);
        }
        return topicStats;
    }


    /**
     * 获取备份状态统计信息
     */
    public Map<String, Object> getBackupStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("backupCount", backupStatusMap.size());
        stats.put("lastCommittedOffsets", lastCommittedOffsetMap);

        // 统计已消费和已提交的备份数量
        long consumedCount = backupStatusMap.values().stream()
                .filter(BackupStatus::isConsumed)
                .count();
        long committedCount = backupStatusMap.values().stream()
                .filter(BackupStatus::isOffsetCommitted)
                .count();

        stats.put("consumedBackupCount", consumedCount);
        stats.put("committedBackupCount", committedCount);

        return stats;
    }

    /**
     * 获取当前消费者状态信息
     *
     * @return 状态信息
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("topic", topic);
        status.put("groupId", consumerGroupId);
        status.put("running", running.get());
        status.put("backupCounter", backupStatusMap.size());
        status.put("lastBackupTimes", lastCommittedOffsetMap);

        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double memoryUsageRatio = (double) usedMemory / maxMemory;

        status.put("memoryUsage", String.format("%.2f%%", memoryUsageRatio * 100));
        status.put("maxMemory", maxMemory / (1024 * 1024) + "MB");
        status.put("usedMemory", usedMemory / (1024 * 1024) + "MB");

        return status;
    }

    /**
     * 启动备份清理任务
     */
    private void startBackupCleanupTask() {
        cleanupExecutor.scheduleAtFixedRate(
                this::cleanupBackups,
                backupProperties.getCleanupIntervalMs(),
                backupProperties.getCleanupIntervalMs(),
                TimeUnit.MILLISECONDS
        );
        log.info("备份清理任务已启动: topic={}, interval={}ms", topic, backupProperties.getCleanupIntervalMs());
    }

    /**
     * 清理备份文件
     */
    private void cleanupBackups() {
        try {
            log.debug("开始清理备份文件: topic={}", topic);
            long currentTime = System.currentTimeMillis();

            // 遍历备份文件信息
            Iterator<Map.Entry<String, BackupStatus>> iterator = backupStatusMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, BackupStatus> entry = iterator.next();
                String partitionKey = entry.getKey();
                BackupStatus status = entry.getValue();

                // 解析备份键
                String[] parts = partitionKey.split(":");
                if (parts.length != 2) {
                    log.warn("无效的备份键格式: {}", partitionKey);
                    continue;
                }

                long committedOffset = lastCommittedOffsetMap.getOrDefault(partitionKey, 0L);

                // 检查是否可以清理
                // 清理条件：
                // 1. 已消费的备份文件
                // 2. 偏移量小于等于已提交的偏移量
                // 3. 超过保留时间的备份文件
                boolean shouldCleanup = status.isConsumed() ||
                        (committedOffset >= 0 && status.getOffset() <= committedOffset) ||
                        (currentTime - status.getCreateTime() > backupProperties.getBackupRetentionMs());

                if (shouldCleanup) {

                    String topic = parts[0];
                    int partition = Integer.parseInt(parts[1]);

                    try {
                        // 删除备份文件
                        deleteBackup(topic, partition);
                        log.info("已删除备份文件: topic={}, partition={}, offset={}, consumed={}, age={}ms",
                                topic, partition, status.getOffset(), status.isConsumed(),
                                currentTime - status.getCreateTime());
                        // 从缓存中移除
                        iterator.remove();
                    } catch (Exception e) {
                        log.error("删除备份文件失败: topic={}, partition={}, offset={}, exception={}",
                                topic, partition, status.getOffset(), e.getMessage(), e);
                    }
                }
            }
            log.debug("备份文件清理完成: topic={}, remainingFiles={}", topic, backupStatusMap.size());
        } catch (Exception e) {
            log.error("清理备份文件异常: topic={}, exception={}", topic, e.getMessage(), e);
        }
    }

    public void deleteBackup(String topic, int partition) {

        // 检查备份目录
        String backupDir = backupProperties.getBackupDir();
        if (!new File(backupDir).exists()) {
            return;
        }

        String backupFilePath = getBackupFilePath(topic, partition);
        File backupFile = new File(backupFilePath);
        FileUtils.deleteQuietly(backupFile);

        String partitionKey = getPartitionKey(topic, partition);
        backupStatusMap.remove(partitionKey);
        lastBackupOffsetMap.remove(partitionKey);

        saveBackupIndex(topic, partition);

        log.info("删除备份成功: {}", backupFilePath);
    }

    private void saveBackupIndexs() {
        for (String key : backupStatusMap.keySet()) {
            String[] parts = key.split(":");
            int partition = Integer.parseInt(parts[1]);
            saveBackupIndex(parts[0], partition);
        }
    }

    /**
     * 保存备份索引（实际执行）
     */
    private void saveBackupIndex(String topic, int partition) {
        try {

            ensureBackupDir();

            // 检查备份目录
            String backupDir = backupProperties.getBackupDir();
            if (!new File(backupDir).exists()) {
                return;
            }

            String indexFilePath = getIndexFilePath(topic, partition);
            File indexFile = new File(backupDir, indexFilePath);

            // 创建要保存的索引数据
            Map<String, Object> indexData = new HashMap<>();
            indexData.put("lastCommittedOffsets", lastCommittedOffsetMap);
            indexData.put("lastBackupOffsetMap", lastBackupOffsetMap);

            // 将备份状态转换为可序列化的格式
            Map<String, Map<String, Object>> serializableStatusMap = new HashMap<>();
            for (Map.Entry<String, BackupStatus> entry : backupStatusMap.entrySet()) {
                BackupStatus status = entry.getValue();
                Map<String, Object> statusData = new HashMap<>();
                statusData.put("offset", status.getOffset());
                statusData.put("createTime", status.getCreateTime());
                statusData.put("consumed", status.isConsumed());
                statusData.put("offsetCommitted", status.isOffsetCommitted());
                serializableStatusMap.put(entry.getKey(), statusData);
            }
            indexData.put("backupStatusMap", serializableStatusMap);

            // 保存为JSON文件
            String jsonContent = JSON.toJSONString(indexData);
            FileUtils.writeStringToFile(indexFile, jsonContent, StandardCharsets.UTF_8);

            log.debug("备份索引已保存: topic={}, partition={}, entries={}", topic, 0, backupStatusMap.size());
        } catch (Exception e) {
            log.error("保存备份索引失败: topic={}, partition={}, exception={}", topic, 0, e.getMessage(), e);
        }
    }

    /**
     * 启动备份索引保存任务
     */
    private void startIndexSaveTask() {
        cleanupExecutor.scheduleAtFixedRate(
                this::saveBackupIndexs,
                backupProperties.getIndexSaveIntervalMs(),
                backupProperties.getIndexSaveIntervalMs(),
                TimeUnit.MILLISECONDS
        );
        log.info("备份索引保存任务已启动: topic={}, interval={}ms", topic, backupProperties.getIndexSaveIntervalMs());
    }

    /**
     * 验证备份文件
     */
    private boolean validateBackupFile(String topic, int partition, long offset) {
        try {
            // 检查备份文件是否存在
            if (!this.hasBackup(topic, partition)) {
                log.warn("备份文件不存在: topic={}, partition={}, offset={}", topic, partition, offset);
                return false;
            }

            // 获取备份记录
            BackupRecord record = this.getBackupRecord(topic, partition);
            if (record == null) {
                log.warn("备份记录不存在: topic={}, partition={}, offset={}", topic, partition, offset);
                return false;
            }

            // 验证偏移量
            if (record.getLastConsumedOffset() != offset) {
                log.warn("备份偏移量不匹配: topic={}, partition={}, expectedOffset={}, actualOffset={}",
                        topic, partition, offset, record.getLastConsumedOffset());
                return false;
            }

            // 验证备份文件
            File backupFile = new File(record.getFilePath());
            if (!backupFile.exists() || !backupFile.isFile()) {
                log.warn("备份文件不存在或不是文件: topic={}, partition={}, offset={}, path={}",
                        topic, partition, offset, record.getFilePath());
                return false;
            }

            // 验证记录数
            List<String> lines = FileUtils.readLines(backupFile, StandardCharsets.UTF_8);
            if (lines.size() != record.getRecordSize()) {
                log.warn("备份文件记录数不匹配: topic={}, partition={}, offset={}, expectedSize={}, actualSize={}",
                        topic, partition, offset, record.getRecordSize(), lines.size());
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("验证备份文件异常: topic={}, partition={}, offset={}, exception={}",
                    topic, partition, offset, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 执行健康检查
     *
     * @return 健康状态
     */
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("details", new HashMap<String, Object>());

        try {
            // 检查备份目录
            String backupDir = backupProperties.getBackupDir();
            File dir = new File(backupDir);
            if (!dir.exists() || !dir.isDirectory() || !dir.canWrite()) {
                health.put("status", "DOWN");
                ((Map<String, Object>) health.get("details")).put("backupDir",
                        "备份目录不存在或无法写入: " + backupDir);
            }

            // 检查内存状态
            if (isLowMemory()) {
                health.put("status", "WARN");
                ((Map<String, Object>) health.get("details")).put("memory", "内存使用率过高");
            }

            // 检查备份索引
            File indexFile = new File(backupDir + File.separator + topic + "-0-index.json");
            if (!indexFile.exists() || !indexFile.canRead()) {
                health.put("status", "WARN");
                ((Map<String, Object>) health.get("details")).put("indexFile",
                        "备份索引文件不存在或无法读取");
            }

            // 检查消费者状态
            if (!running.get()) {
                health.put("status", "DOWN");
                ((Map<String, Object>) health.get("details")).put("consumer",
                        "消费者未运行");
            }

        } catch (Exception e) {
            health.put("status", "DOWN");
            ((Map<String, Object>) health.get("details")).put("exception",
                    e.getMessage());
        }

        return health;
    }

    /**
     * 检查系统内存状态
     *
     * @return 是否内存不足
     */
    private boolean isLowMemory() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double memoryUsageRatio = (double) usedMemory / maxMemory;

        // 添加更多内存指标
        long directMemory = getDirectMemoryUsage(); // 需要实现此方法

        // 记录内存使用情况
        if (memoryUsageRatio > 0.7) {
            log.warn("内存使用率较高: topic={}, memoryUsage={}, directMemory={}MB",
                    topic, String.format("%.2f%%", memoryUsageRatio * 100),
                    directMemory / (1024 * 1024));
        }

        // 多级内存警告
        if (memoryUsageRatio > 0.9) {
            // 严重内存不足
            log.error("严重内存不足，立即触发GC: topic={}, memoryUsage={}",
                    topic, String.format("%.2f%%", memoryUsageRatio * 100));
            System.gc();
            return true;
        } else if (memoryUsageRatio > 0.8) {
            // 内存不足
            return true;
        }

        return false;
    }

    /**
     * 获取直接内存使用量
     */
    private long getDirectMemoryUsage() {
        try {
            Class<?> clazz = Class.forName("java.nio.Bits");
            java.lang.reflect.Field field = clazz.getDeclaredField("reservedMemory");
            field.setAccessible(true);
            return (long) field.get(null);
        } catch (Exception e) {
            log.warn("获取直接内存使用量失败: {}", e.getMessage());
            return 0;
        }
    }
}
