package io.ddd4j.mq.redisstream;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal Redis Stream command port used by the MQ adapter.
 */
public interface RedisStreamOperations extends AutoCloseable {

    String add(String stream, Map<String, String> fields);

    void createGroup(String stream, String group);

    Map<String, List<RedisStreamRecord>> readGroup(
            String group,
            String consumer,
            int count,
            int blockMillis,
            Set<String> streams);

    void ack(String stream, String group, String entryId);

    Object nativeClient();

    @Override
    default void close() {
    }
}
