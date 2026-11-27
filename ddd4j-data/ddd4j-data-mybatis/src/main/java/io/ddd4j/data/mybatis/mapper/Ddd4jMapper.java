package io.ddd4j.data.mybatis.mapper;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 原生 MyBatis 聚合仓储所需的最小 CRUD Mapper 契约。
 *
 * <p>该接口不绑定任何 SQL Provider。业务 Mapper 应通过 XML 或 MyBatis 注解为这些方法提供映射，
 * 从而保持原生 MyBatis 的显式 SQL 模型。</p>
 *
 * @param <P> 持久化对象类型
 */
public interface Ddd4jMapper<P> {

    /**
     * 执行 <code>insert</code> 对应的处理。
     *
     * @param entity 传入此操作的 <code>P</code> 类型 <code>entity</code>
     * @return 当前操作得到的 <code>int</code> 结果
     */
    int insert(P entity);

    /**
     * 执行 <code>update By Id</code> 对应的处理。
     *
     * @param entity 传入此操作的 <code>P</code> 类型 <code>entity</code>
     * @return 当前操作得到的 <code>int</code> 结果
     */
    int updateById(P entity);

    /**
     * 执行 <code>select By Id</code> 对应的处理。
     *
     * @param id 传入此操作的 <code>Serializable</code> 类型 <code>id</code>
     * @return 当前操作得到的 <code>P</code> 结果
     */
    P selectById(Serializable id);

    /**
     * 执行 <code>select Batch Ids</code> 对应的处理。
     *
     * @param ids 传入此操作的 <code>Collection&lt;? extends Serializable&gt;</code> 类型 <code>ids</code>
     * @return 当前操作得到的 <code>List&lt;P&gt;</code> 结果
     */
    List<P> selectBatchIds(Collection<? extends Serializable> ids);

    /**
     * 执行 <code>select List</code> 对应的处理。
     *
     * @return 当前操作得到的 <code>List&lt;P&gt;</code> 结果
     */
    List<P> selectList();
}
