package io.ddd4j.kit.lang;

import cn.hutool.core.util.IdUtil;
import lombok.experimental.UtilityClass;

/**
 * ID 生成工具类（基于 Hutool IdUtil，整合自定义 Snowflake 能力）
 *
 * <p>提供多种 ID 生成能力：
 * <ul>
 *   <li>{@link IdUtil#randomUUID()} - 随机 UUID</li>
 *   <li>{@link IdUtil#simpleUUID()} - 简化的 UUID（无横线）</li>
 *   <li>{@link IdUtil#fastUUID()} - 高性能 UUID</li>
 *   <li>{@link IdUtil#objectId()} - MongoDB ObjectId</li>
 *   <li>{@link IdUtil#nanoId(int)} - NanoId</li>
 *   <li>{@link #nextSnowflakeId(long, long)} - Twitter Snowflake 分布式 ID</li>
 * </ul>
 *
 * <p>关于 Snowflake：
 * <ul>
 *   <li>Hutool 的 {@link IdUtil#getSnowflake(long, long)} 已经使用单例模式，避免 ID 冲突</li>
 *   <li>本类提供的 {@link #nextSnowflakeId} 简化方法直接调用 Hutool 的单例</li>
 *   <li>如果需要完全自定义（如自定义 timeOffset、randomSequenceLimit），建议业务方直接使用 {@link IdUtil}</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 *   // 1. 基础 ID 生成
 *   String uuid = IdKit.randomUUID();
 *   String objectId = IdKit.objectId();
 *   String nanoId = IdKit.nanoId(10);
 *
 *   // 2. Snowflake 分布式 ID（推荐使用，自动单例）
 *   long id1 = IdKit.nextSnowflakeId(1L, 1L);
 *
 *   // 3. 默认 Snowflake（基于 MAC + PID 自动生成 workerId 和 datacenterId）
 *   long id2 = IdKit.nextSnowflakeId();
 * }</pre>
 *
 * @author Loong Wan
 * @since 2.0.x
 */
@UtilityClass
public class IdKit extends IdUtil {

    /**
     * 获取 Snowflake 分布式 ID（使用自定义 workerId 和 dataCenterId）
     *
     * <p>使用 Hutool 的单例模式，避免创建多个 Snowflake 实例导致 ID 冲突。
     *
     * @param workerId     终端 ID（数据范围为 0~31）
     * @param dataCenterId 数据中心 ID（数据范围为 0~31）
     * @return 分布式 ID
     */
    public static long nextSnowflakeId(long workerId, long dataCenterId) {
        return getSnowflake(workerId, dataCenterId).nextId();
    }

    /**
     * 获取 Snowflake 分布式 ID（字符串形式）
     *
     * @param workerId     终端 ID
     * @param dataCenterId 数据中心 ID
     * @return 分布式 ID（字符串）
     */
    public static String nextSnowflakeIdStr(long workerId, long dataCenterId) {
        return getSnowflake(workerId, dataCenterId).nextIdStr();
    }

}
