package io.ddd4j.core.contract;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 数据仓库抽象 SPI（纯 Java，零框架依赖）。
 *
 * <p>各框架/ORM 适配层提供实现：
 * <ul>
 *   <li>Spring: 基于 MyBatis-Plus 的 {@code BaseRepositoryImpl}</li>
 *   <li>Quarkus: 基于 Hibernate Panache 的实现</li>
 *   <li>Javalin: 基于 JDBI 的实现</li>
 * </ul>
 *
 * <p>与 {@link BaseRepository} 的区别：
 * <ul>
 *   <li>{@link BaseRepository} — 旧版 2 泛型（M, Q），支持静态注册表和充血模型</li>
 *   <li>{@link Repository} — 新版 3 泛型（M, Q, P），显式主键类型，更通用</li>
 * </ul>
 *
 * @param <M> 实体类型（Model）
 * @param <Q> 查询条件类型（Query）
 * @param <P> 主键类型（Primary Key）
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
public interface Repository<M, Q, P extends Serializable> {

    /**
     * 根据主键查询实体
     */
    Optional<M> findById(P id);

    /**
     * 根据查询条件查询单个实体
     */
    Optional<M> findOne(Q query);

    /**
     * 根据查询条件查询实体列表
     */
    List<M> findList(Q query);

    /**
     * 根据查询条件统计数量
     */
    long count(Q query);

    /**
     * 插入实体
     */
    M save(M entity);

    /**
     * 批量插入实体
     */
    List<M> saveAll(Collection<M> entities);

    /**
     * 根据主键更新实体
     */
    M updateById(M entity);

    /**
     * 根据主键删除实体
     */
    boolean deleteById(P id);

    /**
     * 根据主键集合批量删除
     */
    long deleteByIds(Collection<P> ids);
}
