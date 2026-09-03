package io.ddd4j.data.external.sequence;

import cn.hutool.core.lang.Snowflake;
import io.ddd4j.kit.lang.IdKit;

/**
 * 全局序列号生成器
 * <p>基于雪花算法（Snowflake）生成全局唯一的分布式 ID</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class GlobalSequence {

    /**
     * 雪花算法 ID 生成器
     */
    private final Snowflake snowflake;

    /**
     * 构造函数
     *
     * @param workerId            工作机器 ID，范围 0~31
     * @param dataCenterId        数据中心 ID，范围 0~255
     * @param useSystemClock      是否使用 SystemClock 获取当前时间戳
     * @param timeOffset          允许时间回拨的毫秒量
     * @param randomSequenceLimit 随机序列号上限
     */
    public GlobalSequence(long workerId, long dataCenterId, boolean useSystemClock,
                          long timeOffset, long randomSequenceLimit) {
        this.snowflake = IdKit.getSnowflake(workerId, dataCenterId, useSystemClock, timeOffset, randomSequenceLimit);
    }

    /**
     * 获取下一个 ID
     *
     * @return 长整型 ID
     */
    public long nextId() {
        return snowflake.nextId();
    }

    /**
     * 获取下一个 ID 的字符串形式
     *
     * @return ID 字符串
     */
    public String nextIdStr() {
        return snowflake.nextIdStr();
    }

    /**
     * 获取雪花算法 ID 生成器
     *
     * @return Snowflake 实例
     */
    public Snowflake getSnowflake() {
        return snowflake;
    }

    /**
     * 关闭资源
     * <p>预留用于释放外部资源</p>
     */
    public void shutdown() {
        // Reserved for implementations that allocate external resources.
    }
}
