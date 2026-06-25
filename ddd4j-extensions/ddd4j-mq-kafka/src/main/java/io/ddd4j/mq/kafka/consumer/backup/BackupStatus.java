package io.ddd4j.mq.kafka.consumer.backup;

import lombok.Getter;
import lombok.Setter;

/**
 * 备份文件状态类
 */
@Getter
@Setter
public class BackupStatus {
    private final long offset;         // 备份的偏移量
    private final long createTime;     // 创建时间
    private boolean consumed;          // 是否已消费
    private boolean offsetCommitted;   // 偏移量是否已提交

    public BackupStatus(long offset, long createTime) {
        this.offset = offset;
        this.createTime = createTime;
        this.consumed = false;
        this.offsetCommitted = false;
    }

}