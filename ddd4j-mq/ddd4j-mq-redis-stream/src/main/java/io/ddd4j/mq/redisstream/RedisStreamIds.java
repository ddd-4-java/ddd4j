package io.ddd4j.mq.redisstream;

import java.util.Objects;

/**
 * Redis Stream ID 工具类，用于从消息 ID 中提取投递标签。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
final class RedisStreamIds {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>RedisStreamIds</code> \u5b9e\u4f8b\u3002 */
    private RedisStreamIds() {
    }

    /**
     * 从 Redis Stream 消息 ID 中提取投递标签（时间戳部分）。
     *
     * @param id Redis Stream 消息 ID（格式：{@code timestamp-sequence}）
     * @return 投递标签值
     */
    static long deliveryTag(String id) {
        if (Objects.isNull(id) || io.ddd4j.kit.lang.StrKit.isBlank(id)) {
            return 0L;
        }
        int dash = id.indexOf('-');
        String time = dash < 0 ? id : id.substring(0, dash);
        try {
            return Long.parseLong(time);
        } catch (NumberFormatException ex) {
            return Math.abs((long) id.hashCode());
        }
    }
}
