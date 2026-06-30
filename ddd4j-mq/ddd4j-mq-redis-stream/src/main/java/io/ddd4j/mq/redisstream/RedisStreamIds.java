package io.ddd4j.mq.redisstream;

final class RedisStreamIds {

    private RedisStreamIds() {
    }

    static long deliveryTag(String id) {
        if (java.util.Objects.isNull(id) || io.ddd4j.kit.lang.StrKit.isBlank(id)) {
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
