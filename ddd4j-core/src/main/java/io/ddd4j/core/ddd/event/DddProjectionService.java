package io.ddd4j.core.ddd.event;

import org.fuin.cqrs4j.esc.ProjectionService;

/**
 * ddd4j 投影服务基类（纯净 DDD 轨道）。
 *
 * <p>基于 fuinorg {@link ProjectionService}，管理 CQRS 读侧的投影位置：
 * <ul>
 *   <li>{@code resetProjectionPosition(streamId)} — 重置投影位置到起点</li>
 *   <li>{@code readProjectionPosition(streamId)} — 读取上次处理到的位置</li>
 *   <li>{@code updateProjectionPosition(streamId, nextEventNumber)} — 更新投影位置</li>
 * </ul>
 *
 * <p>投影服务用于 catch-up subscription 模式：读侧定期从 EventStore 读取新事件，
 * 通过 {@link DddEventDispatcher} 分发到各 {@code JpaView}，并记录处理位置。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ProjectionService
 * @see DddEventDispatcher
 * @since 2.0.x
 */
public abstract class DddProjectionService implements ProjectionService {

    // ProjectionService 接口方法由子类实现

}
