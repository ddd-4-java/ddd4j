package io.ddd4j.data.projection.panache;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * 集成测试 CDI 装配（Task 7.3，H2 内存库 @QuarkusTest 轨专用）。
 *
 * <p>受测的 {@link QuarkusProjectionPositionRepository} 无协作者 Bean（active record
 * 静态委托，见其 javadoc），无需生产者装配；本类仅提供用例间隔离原语：
 * 每用例前清空 {@code ddd4j_projection_position}。写操作需活动事务，
 * 故以 {@code @Transactional} 包装（与 -panache 事件存储模块的
 * {@code PanacheItCdiConfig} 同款模式）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@ApplicationScoped
class ProjectionPanacheItCdiConfig {

    /**
     * 清空投影位置表（用例间隔离）。
     */
    @Transactional
    void clearPositions() {
        PanacheProjectionPositionEntity.deleteAll();
    }
}
