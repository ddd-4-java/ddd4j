package io.ddd4j.core.cqrs.readmodel;

import io.ddd4j.kit.lang.CollKit;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/**
 * 一批从事件流读取出的事件。
 *
 * @param <E> 事件类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Getter
public class EventChunk<E> {

    /** \u6309\u6e90\u4e8b\u4ef6\u987a\u5e8f\u4fdd\u5b58\u7684\u4e8b\u4ef6\u6279\u6b21\u5185\u5bb9\u3002 */
    private final List<E> events;

    /** \u6295\u5f71\u6062\u590d\u65f6\u5e94\u8bfb\u53d6\u7684\u4e0b\u4e00\u6761\u6e90\u4e8b\u4ef6\u5e8f\u53f7\u3002 */
    private final long nextEventNumber;

    /**
     * \u521b\u5efa\u4e0d\u53ef\u53d8\u4e8b\u4ef6\u5757\u3002
     * @param events \u4e8b\u4ef6\u5217\u8868
     * @param nextEventNumber \u4e0b\u4e00\u5f85\u5904\u7406\u4e8b\u4ef6\u53f7\uff0c\u4e0d\u53ef\u4e3a\u8d1f\u6570
     */
    public EventChunk(List<E> events, long nextEventNumber) {
        this.events = List.copyOf(Objects.requireNonNull(events, "events must not be null"));
        if (nextEventNumber < 0) {
            throw new IllegalArgumentException("nextEventNumber must not be negative");
        }
        this.nextEventNumber = nextEventNumber;
    }

    /**
     * \u521b\u5efa\u4e0d\u542b\u4e8b\u4ef6\u7684\u4e8b\u4ef6\u5757\u3002
     * @param <E> \u4e8b\u4ef6\u7c7b\u578b
     * @param nextEventNumber \u4e0b\u4e00\u5f85\u5904\u7406\u4e8b\u4ef6\u53f7
     * @return \u7a7a\u4e8b\u4ef6\u5757
     */
    public static <E> EventChunk<E> empty(long nextEventNumber) {
        return new EventChunk<>(List.of(), nextEventNumber);
    }

    /**
     * \u5224\u65ad\u4e8b\u4ef6\u5757\u662f\u5426\u5305\u542b\u4e8b\u4ef6\u3002
     * @return \u5305\u542b\u4e8b\u4ef6\u65f6\u4e3a true
     */
    public boolean hasEvents() {
        return CollKit.isNotEmpty(events);
    }
}
