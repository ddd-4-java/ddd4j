package io.ddd4j.mq.kafka;

import lombok.Data;

@Data
public class KafkaBackupProperties {

    /**
     * 默认最大批处理大小
     */
    public static final int DEFAULT_MAX_BATCH_SIZE = 100;

    /**
     * 默认最大重试次数
     */
    public static final int DEFAULT_MAX_RETRY_ATTEMPTS = 3;

    /**
     * 默认重试间隔（毫秒）
     */
    public static final long DEFAULT_RETRY_BACKOFF_MS = 1000L;

    /**
     * 默认备份超时时间（毫秒）
     */
    public static final long DEFAULT_BACKUP_TIMEOUT_MS = 10000L;

    /**
     * 默认索引保存间隔（毫秒）
     */
    public static final long DEFAULT_INDEX_SAVE_INTERVAL_MS = 5000L;

    /**
     * 默认清理间隔（毫秒）
     */
    public static final long DEFAULT_CLEANUP_INTERVAL_MS = 300000L;

    /**
     * 默认保留时间（毫秒）- 默认保留1小时，作为安全措施
     */
    public static final int DEFAULT_RETENTION_MS = 3600000;

    /**
     * 默认最大备份文件数
     */
    public static final int DEFAULT_MAX_BACKUP_FILES = 10;

    /**
     * 备份目录
     */
    private String backupDir;

    /**
     * 最大批处理大小
     */
    private int maxBatchSize = DEFAULT_MAX_BATCH_SIZE;

    /**
     * 最大重试次数
     */
    private int maxRetryAttempts = DEFAULT_MAX_RETRY_ATTEMPTS;

    /**
     * 重试间隔（毫秒）
     */
    private long retryBackoffMs = DEFAULT_RETRY_BACKOFF_MS;

    /**
     * 备份超时时间（毫秒）
     */
    private long backupTimeoutMs = DEFAULT_BACKUP_TIMEOUT_MS;

    /**
     * 索引保存间隔（毫秒）
     */
    private long indexSaveIntervalMs = DEFAULT_INDEX_SAVE_INTERVAL_MS;

    /**
     * 是否启用压缩
     */
    private boolean compressionEnabled = false;

    /**
     * 最大备份文件数
     */
    private int maxBackupFiles = DEFAULT_MAX_BACKUP_FILES;

    /**
     * 清理间隔（毫秒）
     */
    private long cleanupIntervalMs = DEFAULT_CLEANUP_INTERVAL_MS;

    /**
     * 保留时间（毫秒）- 默认保留1小时，作为安全措施
     */
    private long backupRetentionMs = DEFAULT_RETENTION_MS;

} 