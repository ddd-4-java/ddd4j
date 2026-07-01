package io.ddd4j.core.ddd.query;

import org.fuin.cqrs4j.core.View;

/**
 * ddd4j 查询视图基类（纯净 DDD 轨道）。
 *
 * <p>基于 fuinorg {@link View}，定义 CQRS 读侧的投影单元：
 * <ul>
 *   <li>{@code getName()} — 视图唯一名称</li>
 *   <li>{@code getEventTypes()} — 本视图关注的事件类型集合</li>
 * </ul>
 *
 * <p>视图是事件投影的目标，负责将领域事件投影为可查询的读模型。
 * 典型场景：将 {@code PersonCreatedEvent} 投影为 {@code PersonListView} 中的一行记录。
 *
 * <p>使用方式：
 * <pre>
 * public class PersonListView extends DddView {
 *     &#64;Override
 *     public String getName() { return "PersonListView"; }
 *
 *     &#64;Override
 *     public Set&lt;EventType&gt; getEventTypes() {
 *         return Set.of(PersonCreatedEvent.TYPE, PersonDeletedEvent.TYPE);
 *     }
 * }
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see View
 * @see DddJpaView
 * @since 2.0.x
 */
public abstract class DddView implements View {

    // View 接口方法由子类实现

}
