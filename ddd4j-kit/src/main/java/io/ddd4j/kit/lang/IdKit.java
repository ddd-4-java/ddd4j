package io.ddd4j.kit.lang;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

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

    /** \u7f13\u5b58\u83b7\u53d6\u5230\u7684\u672c\u673a\u7f51\u7edc\u5730\u5740\u672b\u5c3e\u5b57\u8282\u3002 */
    private static byte LAST_IP = 0;
    /** \u6839\u636e\u8282\u70b9\u3001\u65f6\u949f\u548c\u5e8f\u5217\u914d\u7f6e\u590d\u7528\u7684 Snowflake \u5b9e\u4f8b\u3002 */
    private static final Map<SnowflakeOptions, Snowflake> CUSTOM_SNOWFLAKES = new ConcurrentHashMap<>();

    /**
     * \u83b7\u53d6\u5355\u4f8b\u7684Twitter\u7684Snowflake \u7b97\u6cd5\u751f\u6210\u5668\u5bf9\u8c61<br>
     *  \u5206\u5e03\u5f0f\u7cfb\u7edf\u4e2d\uff0c\u6709\u4e00\u4e9b\u9700\u8981\u4f7f\u7528\u5168\u5c40\u552f\u4e00ID\u7684\u573a\u666f\uff0c\u6709\u4e9b\u65f6\u5019\u6211\u4eec\u5e0c\u671b\u80fd\u4f7f\u7528\u4e00\u79cd\u7b80\u5355\u4e00\u4e9b\u7684ID\uff0c\u5e76\u4e14\u5e0c\u671bID\u80fd\u591f\u6309\u7167\u65f6\u95f4\u6709\u5e8f\u751f\u6210\u3002
     *
     *  <p>
     *  snowflake\u7684\u7ed3\u6784\u5982\u4e0b(\u6bcf\u90e8\u5206\u7528-\u5206\u5f00):<br>
     *
     *  <pre>
     *  0 - 0000000000 0000000000 0000000000 0000000000 0 - 00000 - 00000 - 000000000000
     *  </pre>
     *  <p>
     *  \u7b2c\u4e00\u4f4d\u4e3a\u672a\u4f7f\u7528\uff0c\u63a5\u4e0b\u6765\u768441\u4f4d\u4e3a\u6beb\u79d2\u7ea7\u65f6\u95f4(41\u4f4d\u7684\u957f\u5ea6\u53ef\u4ee5\u4f7f\u752869\u5e74)<br>
     *  \u7136\u540e\u662f5\u4f4ddatacenterId\u548c5\u4f4dworkerId(10\u4f4d\u7684\u957f\u5ea6\u6700\u591a\u652f\u6301\u90e8\u7f721024\u4e2a\u8282\u70b9\uff09<br>
     *  \u6700\u540e12\u4f4d\u662f\u6beb\u79d2\u5185\u7684\u8ba1\u6570\uff0812\u4f4d\u7684\u8ba1\u6570\u987a\u5e8f\u53f7\u652f\u6301\u6bcf\u4e2a\u8282\u70b9\u6bcf\u6beb\u79d2\u4ea7\u751f4096\u4e2aID\u5e8f\u53f7\uff09
     *
     *  <p>
     *  \u53c2\u8003\uff1a<a href="http://www.cnblogs.com/relucent/p/4955340.html">...</a>
     * @param workerId \u5de5\u4f5c\u673a\u5668\u8282\u70b9id,\u6570\u636e\u8303\u56f4\u4e3a0~31
     * @since 1.0.0
     *
     * @return \u96ea\u82b1 ID \u751f\u6210\u5668\u5b9e\u4f8b
     */
    public static Snowflake getSnowflake(long workerId) {
        return Singleton.get(Snowflake.class, workerId);
    }

    /**
     * \u83b7\u53d6\u5355\u4f8b\u7684Twitter\u7684Snowflake \u7b97\u6cd5\u751f\u6210\u5668\u5bf9\u8c61<br>
     *  \u5206\u5e03\u5f0f\u7cfb\u7edf\u4e2d\uff0c\u6709\u4e00\u4e9b\u9700\u8981\u4f7f\u7528\u5168\u5c40\u552f\u4e00ID\u7684\u573a\u666f\uff0c\u6709\u4e9b\u65f6\u5019\u6211\u4eec\u5e0c\u671b\u80fd\u4f7f\u7528\u4e00\u79cd\u7b80\u5355\u4e00\u4e9b\u7684ID\uff0c\u5e76\u4e14\u5e0c\u671bID\u80fd\u591f\u6309\u7167\u65f6\u95f4\u6709\u5e8f\u751f\u6210\u3002
     *
     *  <p>
     *  snowflake\u7684\u7ed3\u6784\u5982\u4e0b(\u6bcf\u90e8\u5206\u7528-\u5206\u5f00):<br>
     *
     *  <pre>
     *  0 - 0000000000 0000000000 0000000000 0000000000 0 - 00000 - 00000 - 000000000000
     *  </pre>
     *  <p>
     *  \u7b2c\u4e00\u4f4d\u4e3a\u672a\u4f7f\u7528\uff0c\u63a5\u4e0b\u6765\u768441\u4f4d\u4e3a\u6beb\u79d2\u7ea7\u65f6\u95f4(41\u4f4d\u7684\u957f\u5ea6\u53ef\u4ee5\u4f7f\u752869\u5e74)<br>
     *  \u7136\u540e\u662f5\u4f4ddatacenterId\u548c5\u4f4dworkerId(10\u4f4d\u7684\u957f\u5ea6\u6700\u591a\u652f\u6301\u90e8\u7f721024\u4e2a\u8282\u70b9\uff09<br>
     *  \u6700\u540e12\u4f4d\u662f\u6beb\u79d2\u5185\u7684\u8ba1\u6570\uff0812\u4f4d\u7684\u8ba1\u6570\u987a\u5e8f\u53f7\u652f\u6301\u6bcf\u4e2a\u8282\u70b9\u6bcf\u6beb\u79d2\u4ea7\u751f4096\u4e2aID\u5e8f\u53f7\uff09
     *
     *  <p>
     *  \u53c2\u8003\uff1a<a href="http://www.cnblogs.com/relucent/p/4955340.html">...</a>
     * @param workerId \u5de5\u4f5c\u673a\u5668\u8282\u70b9id,\u6570\u636e\u8303\u56f4\u4e3a0~31
     * @param dataCenterId \u6570\u636e\u4e2d\u5fc3id,\u6570\u636e\u8303\u56f4\u4e3a0~31
     * @since 1.0.0
     *
     * @return \u96ea\u82b1 ID \u751f\u6210\u5668\u5b9e\u4f8b
     */
    public static Snowflake getSnowflake(long workerId, long dataCenterId) {
        return Singleton.get(Snowflake.class, workerId, dataCenterId);
    }

    /**
     * \u83b7\u53d6\u5355\u4f8b\u7684Twitter\u7684Snowflake \u7b97\u6cd5\u751f\u6210\u5668\u5bf9\u8c61<br>
     *  \u5206\u5e03\u5f0f\u7cfb\u7edf\u4e2d\uff0c\u6709\u4e00\u4e9b\u9700\u8981\u4f7f\u7528\u5168\u5c40\u552f\u4e00ID\u7684\u573a\u666f\uff0c\u6709\u4e9b\u65f6\u5019\u6211\u4eec\u5e0c\u671b\u80fd\u4f7f\u7528\u4e00\u79cd\u7b80\u5355\u4e00\u4e9b\u7684ID\uff0c\u5e76\u4e14\u5e0c\u671bID\u80fd\u591f\u6309\u7167\u65f6\u95f4\u6709\u5e8f\u751f\u6210\u3002
     *
     *  <p>
     *  snowflake\u7684\u7ed3\u6784\u5982\u4e0b(\u6bcf\u90e8\u5206\u7528-\u5206\u5f00):<br>
     *
     *  <pre>
     *  0 - 0000000000 0000000000 0000000000 0000000000 0 - 00000 - 00000 - 000000000000
     *  </pre>
     *  <p>
     *  \u7b2c\u4e00\u4f4d\u4e3a\u672a\u4f7f\u7528\uff0c\u63a5\u4e0b\u6765\u768441\u4f4d\u4e3a\u6beb\u79d2\u7ea7\u65f6\u95f4(41\u4f4d\u7684\u957f\u5ea6\u53ef\u4ee5\u4f7f\u752869\u5e74)<br>
     *  \u7136\u540e\u662f5\u4f4ddatacenterId\u548c5\u4f4dworkerId(10\u4f4d\u7684\u957f\u5ea6\u6700\u591a\u652f\u6301\u90e8\u7f721024\u4e2a\u8282\u70b9\uff09<br>
     *  \u6700\u540e12\u4f4d\u662f\u6beb\u79d2\u5185\u7684\u8ba1\u6570\uff0812\u4f4d\u7684\u8ba1\u6570\u987a\u5e8f\u53f7\u652f\u6301\u6bcf\u4e2a\u8282\u70b9\u6bcf\u6beb\u79d2\u4ea7\u751f4096\u4e2aID\u5e8f\u53f7\uff09
     *
     *  <p>
     *  \u53c2\u8003\uff1a<a href="http://www.cnblogs.com/relucent/p/4955340.html">...</a>
     * @param workerId \u5de5\u4f5c\u673a\u5668\u8282\u70b9id,\u6570\u636e\u8303\u56f4\u4e3a0~31
     * @param dataCenterId \u6570\u636e\u4e2d\u5fc3id,\u6570\u636e\u8303\u56f4\u4e3a0~31
     * @param isUseSystemClock \u662f\u5426\u4f7f\u7528{@link cn.hutool.core.date.SystemClock} \u83b7\u53d6\u5f53\u524d\u65f6\u95f4\u6233
     * @since 1.0.0
     *
     * @return \u96ea\u82b1 ID \u751f\u6210\u5668\u5b9e\u4f8b
     */
    public static Snowflake getSnowflake(long workerId, long dataCenterId, boolean isUseSystemClock) {
        return Singleton.get(Snowflake.class, workerId, dataCenterId, isUseSystemClock);
    }

    /**
     * \u83b7\u53d6\u5355\u4f8b\u7684Twitter\u7684Snowflake \u7b97\u6cd5\u751f\u6210\u5668\u5bf9\u8c61<br>
     *  \u5206\u5e03\u5f0f\u7cfb\u7edf\u4e2d\uff0c\u6709\u4e00\u4e9b\u9700\u8981\u4f7f\u7528\u5168\u5c40\u552f\u4e00ID\u7684\u573a\u666f\uff0c\u6709\u4e9b\u65f6\u5019\u6211\u4eec\u5e0c\u671b\u80fd\u4f7f\u7528\u4e00\u79cd\u7b80\u5355\u4e00\u4e9b\u7684ID\uff0c\u5e76\u4e14\u5e0c\u671bID\u80fd\u591f\u6309\u7167\u65f6\u95f4\u6709\u5e8f\u751f\u6210\u3002
     *
     *  <p>
     *  snowflake\u7684\u7ed3\u6784\u5982\u4e0b(\u6bcf\u90e8\u5206\u7528-\u5206\u5f00):<br>
     *
     *  <pre>
     *  0 - 0000000000 0000000000 0000000000 0000000000 0 - 00000 - 00000 - 000000000000
     *  </pre>
     *  <p>
     *  \u7b2c\u4e00\u4f4d\u4e3a\u672a\u4f7f\u7528\uff0c\u63a5\u4e0b\u6765\u768441\u4f4d\u4e3a\u6beb\u79d2\u7ea7\u65f6\u95f4(41\u4f4d\u7684\u957f\u5ea6\u53ef\u4ee5\u4f7f\u752869\u5e74)<br>
     *  \u7136\u540e\u662f5\u4f4ddatacenterId\u548c5\u4f4dworkerId(10\u4f4d\u7684\u957f\u5ea6\u6700\u591a\u652f\u6301\u90e8\u7f721024\u4e2a\u8282\u70b9\uff09<br>
     *  \u6700\u540e12\u4f4d\u662f\u6beb\u79d2\u5185\u7684\u8ba1\u6570\uff0812\u4f4d\u7684\u8ba1\u6570\u987a\u5e8f\u53f7\u652f\u6301\u6bcf\u4e2a\u8282\u70b9\u6bcf\u6beb\u79d2\u4ea7\u751f4096\u4e2aID\u5e8f\u53f7\uff09
     *
     *  <p>
     *  \u53c2\u8003\uff1a<a href="http://www.cnblogs.com/relucent/p/4955340.html">...</a>
     * @param workerId \u5de5\u4f5c\u673a\u5668\u8282\u70b9id,\u6570\u636e\u8303\u56f4\u4e3a0~31
     * @param dataCenterId \u6570\u636e\u4e2d\u5fc3id,\u6570\u636e\u8303\u56f4\u4e3a0~31
     * @param isUseSystemClock \u662f\u5426\u4f7f\u7528{@link cn.hutool.core.date.SystemClock} \u83b7\u53d6\u5f53\u524d\u65f6\u95f4\u6233
     * @param timeOffset \u5141\u8bb8\u65f6\u95f4\u56de\u62e8\u7684\u6beb\u79d2\u6570
     * @since 1.0.0
     *
     * @return \u96ea\u82b1 ID \u751f\u6210\u5668\u5b9e\u4f8b
     */
    public static Snowflake getSnowflake(long workerId, long dataCenterId, boolean isUseSystemClock, long timeOffset) {
        SnowflakeOptions options = new SnowflakeOptions(workerId, dataCenterId, isUseSystemClock, timeOffset, 0L);
        return CUSTOM_SNOWFLAKES.computeIfAbsent(options, IdKit::newSnowflake);
    }

    /**
     * \u83b7\u53d6\u5355\u4f8b\u7684Twitter\u7684Snowflake \u7b97\u6cd5\u751f\u6210\u5668\u5bf9\u8c61<br>
     *  \u5206\u5e03\u5f0f\u7cfb\u7edf\u4e2d\uff0c\u6709\u4e00\u4e9b\u9700\u8981\u4f7f\u7528\u5168\u5c40\u552f\u4e00ID\u7684\u573a\u666f\uff0c\u6709\u4e9b\u65f6\u5019\u6211\u4eec\u5e0c\u671b\u80fd\u4f7f\u7528\u4e00\u79cd\u7b80\u5355\u4e00\u4e9b\u7684ID\uff0c\u5e76\u4e14\u5e0c\u671bID\u80fd\u591f\u6309\u7167\u65f6\u95f4\u6709\u5e8f\u751f\u6210\u3002
     *
     *  <p>
     *  snowflake\u7684\u7ed3\u6784\u5982\u4e0b(\u6bcf\u90e8\u5206\u7528-\u5206\u5f00):<br>
     *
     *  <pre>
     *  0 - 0000000000 0000000000 0000000000 0000000000 0 - 00000 - 00000 - 000000000000
     *  </pre>
     *  <p>
     *  \u7b2c\u4e00\u4f4d\u4e3a\u672a\u4f7f\u7528\uff0c\u63a5\u4e0b\u6765\u768441\u4f4d\u4e3a\u6beb\u79d2\u7ea7\u65f6\u95f4(41\u4f4d\u7684\u957f\u5ea6\u53ef\u4ee5\u4f7f\u752869\u5e74)<br>
     *  \u7136\u540e\u662f5\u4f4ddatacenterId\u548c5\u4f4dworkerId(10\u4f4d\u7684\u957f\u5ea6\u6700\u591a\u652f\u6301\u90e8\u7f721024\u4e2a\u8282\u70b9\uff09<br>
     *  \u6700\u540e12\u4f4d\u662f\u6beb\u79d2\u5185\u7684\u8ba1\u6570\uff0812\u4f4d\u7684\u8ba1\u6570\u987a\u5e8f\u53f7\u652f\u6301\u6bcf\u4e2a\u8282\u70b9\u6bcf\u6beb\u79d2\u4ea7\u751f4096\u4e2aID\u5e8f\u53f7\uff09
     *
     *  <p>
     *  \u53c2\u8003\uff1a<a href="http://www.cnblogs.com/relucent/p/4955340.html">...</a>
     * @param workerId \u5de5\u4f5c\u673a\u5668\u8282\u70b9id,\u6570\u636e\u8303\u56f4\u4e3a0~31
     * @param dataCenterId \u6570\u636e\u4e2d\u5fc3id,\u6570\u636e\u8303\u56f4\u4e3a0~31
     * @param isUseSystemClock \u662f\u5426\u4f7f\u7528{@link cn.hutool.core.date.SystemClock} \u83b7\u53d6\u5f53\u524d\u65f6\u95f4\u6233
     * @param timeOffset \u5141\u8bb8\u65f6\u95f4\u56de\u62e8\u7684\u6beb\u79d2\u6570
     * @param randomSequenceLimit \u9650\u5b9a\u4e00\u4e2a\u968f\u673a\u4e0a\u9650\uff0c\u5728\u4e0d\u540c\u6beb\u79d2\u4e0b\u751f\u6210\u5e8f\u53f7\u65f6\uff0c\u7ed9\u5b9a\u4e00\u4e2a\u968f\u673a\u6570\uff0c\u907f\u514d\u5076\u6570\u95ee\u9898\uff0c0\u8868\u793a\u65e0\u968f\u673a\uff0c\u4e0a\u9650\u4e0d\u5305\u62ec\u503c\u672c\u8eab\u3002
     * @since 1.0.0
     *
     * @return \u96ea\u82b1 ID \u751f\u6210\u5668\u5b9e\u4f8b
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
        return new Snowflake(null, options.workerId(), options.dataCenterId(), options.useSystemClock(),
                options.timeOffset(), options.randomSequenceLimit());
    }

    /** \u4fdd\u5b58 Snowflake \u8282\u70b9\u7f16\u53f7\u3001\u65f6\u949f\u9009\u62e9\u548c\u5e8f\u5217\u751f\u6210\u914d\u7f6e\u3002 */
    private final static class SnowflakeOptions {

        /** Java 序列化版本标识。 */
        private static final long serialVersionUID = 0L;

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
        @JsonCreator()
        private SnowflakeOptions(@JsonProperty("workerId") long workerId, @JsonProperty("dataCenterId") long dataCenterId, @JsonProperty("useSystemClock") boolean useSystemClock, @JsonProperty("timeOffset") long timeOffset, @JsonProperty("randomSequenceLimit") long randomSequenceLimit) {
            this.workerId = workerId;
            this.dataCenterId = dataCenterId;
            this.useSystemClock = useSystemClock;
            this.timeOffset = timeOffset;
            this.randomSequenceLimit = randomSequenceLimit;
        }

        /**
         * 读取<code>workerId</code> 的当前值。
         *
         * @return 返回表达式 <code>workerId</code>
         */
        @JsonProperty("workerId")
        public long workerId() {
            return workerId;
        }

        /**
         * 读取<code>dataCenterId</code> 的当前值。
         *
         * @return 返回表达式 <code>dataCenterId</code>
         */
        @JsonProperty("dataCenterId")
        public long dataCenterId() {
            return dataCenterId;
        }

        /**
         * 读取<code>useSystemClock</code> 的当前值。
         *
         * @return 返回表达式 <code>useSystemClock</code>
         */
        @JsonProperty("useSystemClock")
        public boolean useSystemClock() {
            return useSystemClock;
        }

        /**
         * 读取<code>timeOffset</code> 的当前值。
         *
         * @return 返回表达式 <code>timeOffset</code>
         */
        @JsonProperty("timeOffset")
        public long timeOffset() {
            return timeOffset;
        }

        /**
         * 读取<code>randomSequenceLimit</code> 的当前值。
         *
         * @return 返回表达式 <code>randomSequenceLimit</code>
         */
        @JsonProperty("randomSequenceLimit")
        public long randomSequenceLimit() {
            return randomSequenceLimit;
        }

        /**
         * 执行 <code>equals</code>，调用 <code>obj.getClass</code>。
         *
         * @param obj 传入此操作的 <code>Object</code> 类型 <code>obj</code>
         * @return 按条件返回 <code>true</code>、<code>false</code>
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            SnowflakeOptions other = (SnowflakeOptions) obj;
            return this.workerId == other.workerId && this.dataCenterId == other.dataCenterId && this.useSystemClock == other.useSystemClock && this.timeOffset == other.timeOffset && this.randomSequenceLimit == other.randomSequenceLimit;
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Long.hashCode(workerId);
            result = 31 * result + Long.hashCode(dataCenterId);
            result = 31 * result + Boolean.hashCode(useSystemClock);
            result = 31 * result + Long.hashCode(timeOffset);
            result = 31 * result + Long.hashCode(randomSequenceLimit);
            return result;
        }

        /**
         * 按 <code>to String</code> 的契约计算并返回结果。
         *
         * @return 当前操作得到的 <code>String</code> 结果
         */
        @Override
        public String toString() {
            return "SnowflakeOptions[workerId=" + workerId + ", dataCenterId=" + dataCenterId + ", useSystemClock=" + useSystemClock + ", timeOffset=" + timeOffset + ", randomSequenceLimit=" + randomSequenceLimit + "]";
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
