package io.ddd4j.core.cqrs.readmodel;

/**
 * 投影位置服务（纯 Java，零框架依赖）。
 *
 * <p>API 形态对齐 {@code cqrs-4-java} 的 ProjectionService 语义，但完全独立实现。
 * 框架适配层（如 {@code ddd4j-runtime-spring}）提供 JPA 实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
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
