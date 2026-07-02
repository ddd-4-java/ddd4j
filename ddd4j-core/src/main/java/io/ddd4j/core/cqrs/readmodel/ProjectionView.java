package io.ddd4j.core.cqrs.readmodel;

import java.util.Collection;

/**
 * 框架无关的增量投影视图。
 *
 * @param <E> 事件类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface ProjectionView<E> {

    /**
     * 视图名称。
     */
    String getName();

    /**
     * 投影流 ID。默认使用视图名称。
     */
    default String getStreamId() {
        return getName();
    }

    /**
     * 定时调度 CRON 表达式。
     */
    String getCron();

    /**
     * 单次读取事件数量。
     */
    default int getChunkSize() {
        return 100;
    }

    /**
     * 本视图关注的事件类型。
     */
    Collection<String> getEventTypes();

    /**
     * 处理一批事件。
     *
     * @param events 事件列表
     */
    void handleEvents(Collection<E> events);
}
