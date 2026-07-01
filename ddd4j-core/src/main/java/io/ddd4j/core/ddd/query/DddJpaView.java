package io.ddd4j.core.ddd.query;

import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.core.JpaView;
import org.fuin.ddd4j.core.Event;

import java.util.List;

/**
 * ddd4j JPA 查询视图基类（纯净 DDD 轨道）。
 *
 * <p>基于 fuinorg {@link JpaView}，定义基于 JPA 的 CQRS 读侧投影：
 * <ul>
 *   <li>{@code getCron()} — 定时更新的 CRON 表达式</li>
 *   <li>{@code getChunkSize()} — 每次读取的事件数量（默认 100）</li>
 *   <li>{@code handleEvents(em, events)} — 处理事件列表，更新 JPA 视图</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 * public class PersonListViewImpl extends DddJpaView {
 *
 *     &#64;Override
 *     public String getName() { return "PersonListView"; }
 *
 *     &#64;Override
 *     public Set&lt;EventType&gt; getEventTypes() {
 *         return Set.of(PersonCreatedEvent.TYPE, PersonDeletedEvent.TYPE);
 *     }
 *
 *     &#64;Override
 *     public String getCron() { return "0 /1 * * * ?"; } // 每分钟
 *
 *     &#64;Override
 *     public void handleEvents(EntityManager em, List&lt;Event&gt; events) {
 *         for (Event event : events) {
 *             if (event instanceof PersonCreatedEvent e) {
 *                 em.persist(new PersonListView(e.getEntityId(), e.getName()));
 *             } else if (event instanceof PersonDeletedEvent e) {
 *                 em.createQuery("DELETE FROM PersonListView WHERE id = :id")
 *                   .setParameter("id", e.getEntityId().asString())
 *                   .executeUpdate();
 *             }
 *         }
 *     }
 * }
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see JpaView
 * @see DddView
 * @since 2.0.x
 */
public abstract class DddJpaView extends DddView implements JpaView {

    /**
     * 默认每次处理 100 个事件。
     */
    @Override
    public int getChunkSize() {
        return 100;
    }

    /**
     * 处理事件列表，更新 JPA 视图。
     *
     * @param em     实体管理器
     * @param events 事件列表
     */
    @Override
    public abstract void handleEvents(EntityManager em, List<Event> events);

}
