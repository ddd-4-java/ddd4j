package io.ddd4j.sample.quarkus.cqrs.shared;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
/** 单实例有界中继；真实事件提交后确认，失败按原消息 ID 重试。 */
@ApplicationScoped
public class OrderOutboxRelay {
    @Inject SharedOrderStorage storage;
    @Inject OrderEventSource source;
    /** 处理最多一百条消息；本例不启动 MQ 消费者。 */
    @Scheduled(every="5s", delayed="5s", concurrentExecution=Scheduled.ConcurrentExecution.SKIP)
    public synchronized void relay() {
        storage.transaction().execute(() -> {
            for (var message : storage.outbox().pending(100)) {
                source.append(message);
                storage.outbox().markPublished(message.id());
            }
        });
    }
}
