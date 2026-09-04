package io.ddd4j.kit.lang;

import cn.hutool.core.lang.Singleton;
import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
@UtilityClass
public class IdKit extends IdUtil {

    private static byte LAST_IP = 0;
    private static final Map<SnowflakeOptions, Snowflake> CUSTOM_SNOWFLAKES = new ConcurrentHashMap<>();

    /**
     * 获取单例的Twitter的Snowflake 算法生成器对象<br>
     * 分布式系统中，有一些需要使用全局唯一ID的场景，有些时候我们希望能使用一种简单一些的ID，并且希望ID能够按照时间有序生成。
     *
     * <p>
     * snowflake的结构如下(每部分用-分开):<br>
     *
     * <pre>
     * 0 - 0000000000 0000000000 0000000000 0000000000 0 - 00000 - 00000 - 000000000000
     * </pre>
     * <p>
     * 第一位为未使用，接下来的41位为毫秒级时间(41位的长度可以使用69年)<br>
     * 然后是5位datacenterId和5位workerId(10位的长度最多支持部署1024个节点）<br>
     * 最后12位是毫秒内的计数（12位的计数顺序号支持每个节点每毫秒产生4096个ID序号）
     *
     * <p>
     * 参考：<a href="http://www.cnblogs.com/relucent/p/4955340.html">...</a>
     *
     * @param workerId 工作机器节点id,数据范围为0~31
     * @since 1.0.0
     */
    public static Snowflake getSnowflake(long workerId) {
        return Singleton.get(Snowflake.class, workerId);
    }

    /**
     * 获取单例的Twitter的Snowflake 算法生成器对象<br>
     * 分布式系统中，有一些需要使用全局唯一ID的场景，有些时候我们希望能使用一种简单一些的ID，并且希望ID能够按照时间有序生成。
     *
     * <p>
     * snowflake的结构如下(每部分用-分开):<br>
     *
     * <pre>
     * 0 - 0000000000 0000000000 0000000000 0000000000 0 - 00000 - 00000 - 000000000000
     * </pre>
     * <p>
     * 第一位为未使用，接下来的41位为毫秒级时间(41位的长度可以使用69年)<br>
     * 然后是5位datacenterId和5位workerId(10位的长度最多支持部署1024个节点）<br>
     * 最后12位是毫秒内的计数（12位的计数顺序号支持每个节点每毫秒产生4096个ID序号）
     *
     * <p>
     * 参考：<a href="http://www.cnblogs.com/relucent/p/4955340.html">...</a>
     *
     * @param workerId     工作机器节点id,数据范围为0~31
     * @param dataCenterId 数据中心id,数据范围为0~31
     * @since 1.0.0
     */
    public static Snowflake getSnowflake(long workerId, long dataCenterId) {
        return Singleton.get(Snowflake.class, workerId, dataCenterId);
    }

    /**
     * 获取单例的Twitter的Snowflake 算法生成器对象<br>
     * 分布式系统中，有一些需要使用全局唯一ID的场景，有些时候我们希望能使用一种简单一些的ID，并且希望ID能够按照时间有序生成。
     *
     * <p>
     * snowflake的结构如下(每部分用-分开):<br>
     *
     * <pre>
     * 0 - 0000000000 0000000000 0000000000 0000000000 0 - 00000 - 00000 - 000000000000
     * </pre>
     * <p>
     * 第一位为未使用，接下来的41位为毫秒级时间(41位的长度可以使用69年)<br>
     * 然后是5位datacenterId和5位workerId(10位的长度最多支持部署1024个节点）<br>
     * 最后12位是毫秒内的计数（12位的计数顺序号支持每个节点每毫秒产生4096个ID序号）
     *
     * <p>
     * 参考：<a href="http://www.cnblogs.com/relucent/p/4955340.html">...</a>
     *
     * @param workerId         工作机器节点id,数据范围为0~31
     * @param dataCenterId     数据中心id,数据范围为0~31
     * @param isUseSystemClock 是否使用{@link cn.hutool.core.date.SystemClock} 获取当前时间戳
     * @since 1.0.0
     */
    public static Snowflake getSnowflake(long workerId, long dataCenterId, boolean isUseSystemClock) {
        return Singleton.get(Snowflake.class, workerId, dataCenterId, isUseSystemClock);
    }

    /**
     * 获取单例的Twitter的Snowflake 算法生成器对象<br>
     * 分布式系统中，有一些需要使用全局唯一ID的场景，有些时候我们希望能使用一种简单一些的ID，并且希望ID能够按照时间有序生成。
     *
     * <p>
     * snowflake的结构如下(每部分用-分开):<br>
     *
     * <pre>
     * 0 - 0000000000 0000000000 0000000000 0000000000 0 - 00000 - 00000 - 000000000000
     * </pre>
     * <p>
     * 第一位为未使用，接下来的41位为毫秒级时间(41位的长度可以使用69年)<br>
     * 然后是5位datacenterId和5位workerId(10位的长度最多支持部署1024个节点）<br>
     * 最后12位是毫秒内的计数（12位的计数顺序号支持每个节点每毫秒产生4096个ID序号）
     *
     * <p>
     * 参考：<a href="http://www.cnblogs.com/relucent/p/4955340.html">...</a>
     *
     * @param workerId         工作机器节点id,数据范围为0~31
     * @param dataCenterId     数据中心id,数据范围为0~31
     * @param isUseSystemClock 是否使用{@link cn.hutool.core.date.SystemClock} 获取当前时间戳
     * @param timeOffset       允许时间回拨的毫秒数
     * @since 1.0.0
     */
    public static Snowflake getSnowflake(long workerId, long dataCenterId, boolean isUseSystemClock, long timeOffset) {
        SnowflakeOptions options = new SnowflakeOptions(workerId, dataCenterId, isUseSystemClock, timeOffset, 0L);
        return CUSTOM_SNOWFLAKES.computeIfAbsent(options, IdKit::newSnowflake);
    }

    /**
     * 获取单例的Twitter的Snowflake 算法生成器对象<br>
     * 分布式系统中，有一些需要使用全局唯一ID的场景，有些时候我们希望能使用一种简单一些的ID，并且希望ID能够按照时间有序生成。
     *
     * <p>
     * snowflake的结构如下(每部分用-分开):<br>
     *
     * <pre>
     * 0 - 0000000000 0000000000 0000000000 0000000000 0 - 00000 - 00000 - 000000000000
     * </pre>
     * <p>
     * 第一位为未使用，接下来的41位为毫秒级时间(41位的长度可以使用69年)<br>
     * 然后是5位datacenterId和5位workerId(10位的长度最多支持部署1024个节点）<br>
     * 最后12位是毫秒内的计数（12位的计数顺序号支持每个节点每毫秒产生4096个ID序号）
     *
     * <p>
     * 参考：<a href="http://www.cnblogs.com/relucent/p/4955340.html">...</a>
     *
     * @param workerId            工作机器节点id,数据范围为0~31
     * @param dataCenterId        数据中心id,数据范围为0~31
     * @param isUseSystemClock    是否使用{@link cn.hutool.core.date.SystemClock} 获取当前时间戳
     * @param timeOffset          允许时间回拨的毫秒数
     * @param randomSequenceLimit 限定一个随机上限，在不同毫秒下生成序号时，给定一个随机数，避免偶数问题，0表示无随机，上限不包括值本身。
     * @since 1.0.0
     */
    public static Snowflake getSnowflake(long workerId, long dataCenterId, boolean isUseSystemClock, long timeOffset, long randomSequenceLimit) {
        SnowflakeOptions options = new SnowflakeOptions(
                workerId, dataCenterId, isUseSystemClock, timeOffset, randomSequenceLimit);
        return CUSTOM_SNOWFLAKES.computeIfAbsent(options, IdKit::newSnowflake);
    }

    private static Snowflake newSnowflake(SnowflakeOptions options) {
        return new Snowflake(null, options.workerId, options.dataCenterId, options.useSystemClock,
                options.timeOffset, options.randomSequenceLimit);
    }

    private static final class SnowflakeOptions {
        private final long workerId;
        private final long dataCenterId;
        private final boolean useSystemClock;
        private final long timeOffset;
        private final long randomSequenceLimit;

        private SnowflakeOptions(long workerId, long dataCenterId, boolean useSystemClock,
                                 long timeOffset, long randomSequenceLimit) {
            this.workerId = workerId;
            this.dataCenterId = dataCenterId;
            this.useSystemClock = useSystemClock;
            this.timeOffset = timeOffset;
            this.randomSequenceLimit = randomSequenceLimit;
        }
    }

    /**
     * 获取 Snowflake 分布式 ID（使用自定义 workerId 和 dataCenterId）
     *
     * <p>使用 Hutool 的单例模式，避免创建多个 Snowflake 实例导致 ID 冲突。
     *
     * @param workerId 终端 ID（数据范围为 0~31）
     * @return 分布式 ID
     */
    public static long nextSnowflakeId(long workerId) {
        return getSnowflake(workerId).nextId();
    }

    /**
     * 获取 Snowflake 分布式 ID（字符串形式）
     *
     * @param workerId 终端 ID
     * @return 分布式 ID（字符串）
     */
    public static String nextSnowflakeIdStr(long workerId) {
        return getSnowflake(workerId).nextIdStr();
    }

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

    /**
     * 获取 Snowflake 分布式 ID（支持系统时钟配置）
     *
     * @param workerId         终端 ID（数据范围为 0~31）
     * @param dataCenterId     数据中心 ID（数据范围为 0~31）
     * @param isUseSystemClock 是否使用 SystemClock 获取当前时间戳
     * @return 分布式 ID
     */
    public static long nextSnowflakeId(long workerId, long dataCenterId, boolean isUseSystemClock) {
        return getSnowflake(workerId, dataCenterId, isUseSystemClock).nextId();
    }

    /**
     * 获取 Snowflake 分布式 ID 字符串（支持系统时钟配置）
     *
     * @param workerId         终端 ID（数据范围为 0~31）
     * @param dataCenterId     数据中心 ID（数据范围为 0~31）
     * @param isUseSystemClock 是否使用 SystemClock 获取当前时间戳
     * @return 分布式 ID（字符串）
     */
    public static String nextSnowflakeIdStr(long workerId, long dataCenterId, boolean isUseSystemClock) {
        return getSnowflake(workerId, dataCenterId, isUseSystemClock).nextIdStr();
    }

    /**
     * 获取 Snowflake 分布式 ID（支持系统时钟 + 时间回拨配置）
     *
     * @param workerId         终端 ID（数据范围为 0~31）
     * @param dataCenterId     数据中心 ID（数据范围为 0~31）
     * @param isUseSystemClock 是否使用 SystemClock 获取当前时间戳
     * @param timeOffset       允许时间回拨的毫秒数
     * @return 分布式 ID
     */
    public static long nextSnowflakeId(long workerId, long dataCenterId, boolean isUseSystemClock, long timeOffset) {
        return getSnowflake(workerId, dataCenterId, isUseSystemClock, timeOffset).nextId();
    }

    /**
     * 获取 Snowflake 分布式 ID 字符串（支持系统时钟 + 时间回拨配置）
     *
     * @param workerId         终端 ID（数据范围为 0~31）
     * @param dataCenterId     数据中心 ID（数据范围为 0~31）
     * @param isUseSystemClock 是否使用 SystemClock 获取当前时间戳
     * @param timeOffset       允许时间回拨的毫秒数
     * @return 分布式 ID（字符串）
     */
    public static String nextSnowflakeIdStr(long workerId, long dataCenterId, boolean isUseSystemClock, long timeOffset) {
        return getSnowflake(workerId, dataCenterId, isUseSystemClock, timeOffset).nextIdStr();
    }

    /**
     * 获取 Snowflake 分布式 ID（支持系统时钟 + 时间回拨 + 随机序号配置）
     *
     * @param workerId            终端 ID（数据范围为 0~31）
     * @param dataCenterId        数据中心 ID（数据范围为 0~31）
     * @param isUseSystemClock    是否使用 SystemClock 获取当前时间戳
     * @param timeOffset          允许时间回拨的毫秒数
     * @param randomSequenceLimit 限定一个随机上限，0 表示无随机，上限不包括值本身
     * @return 分布式 ID
     */
    public static long nextSnowflakeId(long workerId, long dataCenterId, boolean isUseSystemClock, long timeOffset, long randomSequenceLimit) {
        return getSnowflake(workerId, dataCenterId, isUseSystemClock, timeOffset, randomSequenceLimit).nextId();
    }

    /**
     * 获取 Snowflake 分布式 ID 字符串（支持系统时钟 + 时间回拨 + 随机序号配置）
     *
     * @param workerId            终端 ID（数据范围为 0~31）
     * @param dataCenterId        数据中心 ID（数据范围为 0~31）
     * @param isUseSystemClock    是否使用 SystemClock 获取当前时间戳
     * @param timeOffset          允许时间回拨的毫秒数
     * @param randomSequenceLimit 限定一个随机上限，0 表示无随机，上限不包括值本身
     * @return 分布式 ID（字符串）
     */
    public static String nextSnowflakeIdStr(long workerId, long dataCenterId, boolean isUseSystemClock, long timeOffset, long randomSequenceLimit) {
        return getSnowflake(workerId, dataCenterId, isUseSystemClock, timeOffset, randomSequenceLimit).nextIdStr();
    }

    /**
     * 用IP地址最后几个字节标示
     * <p>
     * eg:192.168.1.30->30
     *
     * @return last IP
     */
    public static byte getLastIPAddress() {
        if (LAST_IP != 0) {
            return LAST_IP;
        }

        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            byte[] addressByte = inetAddress.getAddress();
            LAST_IP = addressByte[addressByte.length - 1];
            log.info("Host Address : {}", inetAddress.getHostAddress());
        } catch (Exception e) {
            throw new RuntimeException("Unknown Host Exception", e);
        }

        return LAST_IP;
    }
}
