package io.ddd4j.sample.order.application;

import java.util.List;

public interface OutboxPort {
    void append(List<OutboxMessage> messages);
    List<OutboxMessage> pending(int limit);
    void markPublished(String messageId);
    void markFailed(String messageId, String reason);
}
