package io.ddd4j.boot.cmpt.kafka.consumer.backup;

import lombok.Data;

/**
 * Kafka 消息备份记录
 */
@Data
public class BackupRecord {

    /**
     * 备份文件路径
     */
    private String filePath;

    /**
     * 消息主题
     */
    private String topic;

    /**
     * 备份时间
     */
    private Long backupTime;

    /**
     * 最后消费的原偏移量
     */
    private Long lastConsumedOffset;

    /**
     * 记录数
     */
    private int recordSize;

}
