package io.ddd4j.data.mybatis.repository.impl;

import io.ddd4j.data.mybatis.repository.MybatisAggregateRepository;
import org.apache.ibatis.enhance.mapper.EnhanceMapper;

/**
 * 业务方入口基类（对齐 mybatisplus 模块的 BaseRepositoryImpl）。
 *
 * <p>子类只需声明五泛型并提供无参构造器供 DI 容器使用：</p>
 * <pre>{@code
 * public class OrderRepository
 *         extends BaseRepositoryImpl<OrderMapper, Order, OrderPO, OrderQuery, Long> {
 * }
 * }</pre>
 *
 * @param <MP> Mapper 类型（须继承 {@link EnhanceMapper}）
 * @param <M>  聚合根类型
 * @param <P>  持久化对象类型
 * @param <Q>  充血查询类型
 * @param <ID> 标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public abstract class BaseRepositoryImpl<
        MP extends EnhanceMapper<P>,
        M extends io.ddd4j.core.ddd.model.AggregateRoot<?>,
        P,
        Q extends io.ddd4j.core.cqrs.query.Query<M>,
        ID extends java.io.Serializable>
        extends MybatisAggregateRepository<MP, M, P, Q, ID> {
}
