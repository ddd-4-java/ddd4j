/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    /** \u6839\u636e\u8282\u70b9\u3001\u65f6\u949f\u548c\u5e8f\u5217\u914d\u7f6e\u590d\u7528\u7684 Snowflake \u5b9e\u4f8b\u3002 */
    private static final Map<SnowflakeOptions, Snowflake> CUSTOM_SNOWFLAKES = new ConcurrentHashMap<>();
    /** \u7f13\u5b58\u83b7\u53d6\u5230\u7684\u672c\u673a\u7f51\u7edc\u5730\u5740\u672b\u5c3e\u5b57\u8282\u3002 */
    private static byte LAST_IP = 0;

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
     * @return 雪花 ID 生成器实例
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
     * @return 雪花 ID 生成器实例
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
     * @return 雪花 ID 生成器实例
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
     * @return 雪花 ID 生成器实例
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
     * @return 雪花 ID 生成器实例
     * @since 1.0.0
     */
    public static Snowflake getSnowflake(long workerId, long dataCenterId, boolean isUseSystemClock, long timeOffset, long randomSequenceLimit) {
        SnowflakeOptions options = new SnowflakeOptions(
                workerId, dataCenterId, isUseSystemClock, timeOffset, randomSequenceLimit);
        return CUSTOM_SNOWFLAKES.computeIfAbsent(options, IdKit::newSnowflake);
    }

    /**
     * \u7528\u5de5\u4f5c\u8282\u70b9\u3001\u6570\u636e\u4e2d\u5fc3\u548c\u65f6\u949f\u9009\u9879\u6784\u9020 Hutool Snowflake\u3002
     * @param options Snowflake \u751f\u6210\u914d\u7f6e
     * @return \u7b26\u5408\u9009\u9879\u7684 ID \u751f\u6210\u5668
     */
    private static Snowflake newSnowflake(SnowflakeOptions options) {
        return new Snowflake(null, options.workerId, options.dataCenterId, options.useSystemClock,
                options.timeOffset, options.randomSequenceLimit);
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
     * eg：IP 192.168.1.30 取最后一段 30
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

    /** \u4fdd\u5b58 Snowflake \u8282\u70b9\u7f16\u53f7\u3001\u65f6\u949f\u9009\u62e9\u548c\u5e8f\u5217\u751f\u6210\u914d\u7f6e\u3002 */
    private static final class SnowflakeOptions {
        /** Snowflake \u6807\u8bc6\u4e2d\u7684\u5de5\u4f5c\u8282\u70b9\u7f16\u53f7\u3002 */
        private final long workerId;
        /** Snowflake \u6807\u8bc6\u4e2d\u7684\u6570\u636e\u4e2d\u5fc3\u7f16\u53f7\u3002 */
        private final long dataCenterId;
        /** \u662f\u5426\u4f7f\u7528 Hutool \u7cfb\u7edf\u65f6\u949f\u8bfb\u53d6\u65f6\u95f4\u3002 */
        private final boolean useSystemClock;
        /** \u5bb9\u8bb8\u65f6\u949f\u56de\u62e8\u7684\u65f6\u95f4\u5dee\uff0c\u5355\u4f4d\u4e3a\u6beb\u79d2\u3002 */
        private final long timeOffset;
        /** \u6beb\u79d2\u5185\u521d\u59cb\u968f\u673a\u5e8f\u5217\u7684\u4e0a\u9650\u3002 */
        private final long randomSequenceLimit;

        /**
         * \u4fdd\u5b58\u7528\u4e8e\u521b\u5efa\u548c\u7f13\u5b58 Snowflake \u7684\u5b8c\u6574\u914d\u7f6e\u3002
         * @param workerId Snowflake \u6807\u8bc6\u4e2d\u7684\u5de5\u4f5c\u8282\u70b9\u7f16\u53f7
         * @param dataCenterId Snowflake \u6807\u8bc6\u4e2d\u7684\u6570\u636e\u4e2d\u5fc3\u7f16\u53f7
         * @param useSystemClock \u662f\u5426\u4f7f\u7528 Hutool \u7cfb\u7edf\u65f6\u949f\u8bfb\u53d6\u65f6\u95f4
         * @param timeOffset \u5bb9\u8bb8\u65f6\u949f\u56de\u62e8\u7684\u65f6\u95f4\u5dee\uff0c\u5355\u4f4d\u4e3a\u6beb\u79d2
         * @param randomSequenceLimit \u6beb\u79d2\u5185\u521d\u59cb\u968f\u673a\u5e8f\u5217\u7684\u4e0a\u9650
         */
        private SnowflakeOptions(long workerId, long dataCenterId, boolean useSystemClock,
                                 long timeOffset, long randomSequenceLimit) {
            this.workerId = workerId;
            this.dataCenterId = dataCenterId;
            this.useSystemClock = useSystemClock;
            this.timeOffset = timeOffset;
            this.randomSequenceLimit = randomSequenceLimit;
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof SnowflakeOptions)) {
                return false;
            }
            SnowflakeOptions that = (SnowflakeOptions) object;
            return workerId == that.workerId
                    && dataCenterId == that.dataCenterId
                    && useSystemClock == that.useSystemClock
                    && timeOffset == that.timeOffset
                    && randomSequenceLimit == that.randomSequenceLimit;
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = (int) (workerId ^ (workerId >>> 32));
            result = 31 * result + (int) (dataCenterId ^ (dataCenterId >>> 32));
            result = 31 * result + (useSystemClock ? 1231 : 1237);
            result = 31 * result + (int) (timeOffset ^ (timeOffset >>> 32));
            result = 31 * result + (int) (randomSequenceLimit ^ (randomSequenceLimit >>> 32));
            return result;
        }
    }
}
