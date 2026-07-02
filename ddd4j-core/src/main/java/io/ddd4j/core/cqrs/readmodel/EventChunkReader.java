package io.ddd4j.core.cqrs.readmodel;

import java.util.Collection;

/**
 * 事件块读取器 SPI。
 *
 * <p>业务或框架适配层负责把具体事件存储（数据库、MQ、EventStoreDB、文件等）
 * 适配成该接口；核心层只关心从某个位置读取一批事件。
 *
 * @param <E> 事件类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface EventChunkReader<E> {

    /**
     * 从指定位置读取事件。
     *
     * @param streamId        投影流 ID
     * @param fromEventNumber 起始事件号，包含该位置
     * @param chunkSize       本次最大读取数量
     * @param eventTypes      需要读取的事件类型；为空表示由实现自行决定
     * @return 事件块
     */
    EventChunk<E> read(String streamId, long fromEventNumber, int chunkSize, Collection<String> eventTypes);
}
