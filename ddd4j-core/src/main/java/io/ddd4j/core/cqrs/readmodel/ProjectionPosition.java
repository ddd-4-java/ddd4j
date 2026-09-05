package io.ddd4j.core.cqrs.readmodel;

import java.io.Serializable;

/**
 * 投影位置（Projection Position）—— 标识某个 CQRS 读模型视图的增量拉取偏移量。
 *
 * <p>读侧视图通过 {@link ProjectionPosition} 跟踪已经从 EventStore 拉取到的事件位置，
 * 重启后可从上次位置继续拉取，不丢消息、不重复处理。
 *
 * <p>本接口为 ddd4j 通用基础层的纯 Java SPI，<b>无任何框架依赖</b>。
 * 具体持久化（关系数据库、Redis、文件）由各框架适配层（{@code ddd4j-runtime-spring}、
 * {@code ddd4j-runtime-quarkus}、{@code ddd4j-javalin}）实现。
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@code streamId}：EventStore 中该投影对应的流 ID（业务唯一）</li>
 *   <li>{@code nextEventNumber}：下一个待处理事件号（包含 0）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ViewManager
 * @see ProjectionPositionRepository
 * @since 2.0.x
 */
public interface ProjectionPosition extends Serializable {
    String getStreamId();
    long getNextEventNumber();
    ProjectionPosition withNextEventNumber(long nextEventNumber);
}
