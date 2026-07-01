package io.ddd4j.core.cqrs.projection;

/**
 * 投影位置服务。
 *
 * <p>对齐 {@code cqrs-4-java} 的 ProjectionService 语义，但不继承
 * {@code org.fuin.*} API，保持 ddd4j-core 纯 Java 小内核可独立使用。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
public interface ProjectionService {

    /**
     * 重置投影位置到事件流起点。
     *
     * @param streamId 投影流 ID
     */
    void resetProjectionPosition(String streamId);

    /**
     * 读取下一个待处理事件号。
     *
     * @param streamId 投影流 ID
     * @return 下一个待处理事件号，不存在时返回 0
     */
    long readProjectionPosition(String streamId);

    /**
     * 更新投影位置。
     *
     * @param streamId        投影流 ID
     * @param nextEventNumber 下一个待处理事件号
     * @return 持久化后的投影位置
     */
    ProjectionPosition updateProjectionPosition(String streamId, long nextEventNumber);
}
