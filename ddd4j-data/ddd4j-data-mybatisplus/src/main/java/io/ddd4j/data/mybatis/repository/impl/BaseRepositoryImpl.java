package io.ddd4j.data.mybatis.repository.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.data.mybatis.repository.MybatisAggregateRepository;

import java.io.Serializable;

/**
 * MyBatis-Plus 仓储实现基类（四泛型，向后兼容旧 ddd4j 业务代码）。
 *
 * <p>旧 ddd4j（cloud-agents 等）业务代码通常继承四泛型：
 * <pre>{@code
 * public class AgentRepositoryImpl
 *         extends BaseRepositoryImpl<AgentMapper, Agent, AgentPO, AgentQuery>
 *         implements AgentRepository {
 * }
 * }</pre>
 *
 * <h3>泛型参数</h3>
 * <table border="1">
 *   <tr><th>参数</th><th>含义</th><th>示例</th></tr>
 *   <tr><td>{@code MP}</td><td>MyBatis-Plus Mapper 接口</td><td>{@code AgentMapper}</td></tr>
 *   <tr><td>{@code M}</td><td>聚合根类型（领域模型）</td><td>{@code Agent}</td></tr>
 *   <tr><td>{@code P}</td><td>持久化对象类型（PO，数据库实体）</td><td>{@code AgentPO}</td></tr>
 *   <tr><td>{@code Q}</td><td>查询对象类型（继承 {@link Query}）</td><td>{@code AgentQuery}</td></tr>
 * </table>
 *
 * <h3>实现说明</h3>
 * <p>本类是 {@link MybatisAggregateRepository} 的四泛型别名，保留旧 ddd4j 的类名和泛型签名，
 * 方便 cloud-agents 等旧项目平滑迁移。实现已收敛到 {@link MybatisAggregateRepository}，
 * 不再依赖旧 {@code Model/BaseRepository} 静态注册轨道。
 *
 * <h3>充血查询支持</h3>
 * <p>构造期自动通过 {@link #configureTypes} 注册到 {@link io.ddd4j.core.ddd.repository.RepositoryRegistry}，
 * 业务方的 Query 子类（继承 {@link io.ddd4j.data.mybatis.query.AbstractMybatisQuery}）可直接使用充血查询：
 * <pre>{@code
 * // 字段后缀自动映射
 * List<Agent> list = new AgentQuery().list();
 *
 * // Lambda 链式 + 充血查询
 * AgentQuery query = new AgentQuery();
 * query.lambda(AgentPO.class).eq(AgentPO::getStatus, 1);
 * List<Agent> list = query.list();
 * }</pre>
 *
 * @param <MP> MyBatis-Plus Mapper 接口类型
 * @param <M>  聚合根类型（领域模型）
 * @param <P>  持久化对象类型（PO）
 * @param <Q>  查询对象类型（继承 {@link Query}）
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@SuppressWarnings("unchecked")
public abstract class BaseRepositoryImpl<MP extends BaseMapper<P>, M extends AggregateRoot<?>, P, Q extends Query>
        extends MybatisAggregateRepository<M, P, Serializable> {

    /**
     * 默认构造器，通过泛型反射自动解析 Model/PO/Query 类型并注册到 RepositoryRegistry。
     *
     * <p>子类必须保留无参构造器（泛型反射依赖 {@code getClass()} 获取实际泛型参数）。
     */
    protected BaseRepositoryImpl() {
        super();
        configureTypes(resolveModelClass(), resolvePersistenceObjectClass(), resolveQueryClass());
    }

    /**
     * 解析聚合根类型（泛型参数 M）。
     */
    private Class<M> resolveModelClass() {
        return (Class<M>) ReflectionKit.getSuperClassGenericType(this.getClass(), BaseRepositoryImpl.class, 1);
    }

    /**
     * 解析持久化对象类型（泛型参数 P）。
     */
    private Class<P> resolvePersistenceObjectClass() {
        return (Class<P>) ReflectionKit.getSuperClassGenericType(this.getClass(), BaseRepositoryImpl.class, 2);
    }

    /**
     * 解析查询对象类型（泛型参数 Q）。
     */
    private Class<Q> resolveQueryClass() {
        return (Class<Q>) ReflectionKit.getSuperClassGenericType(this.getClass(), BaseRepositoryImpl.class, 3);
    }

    /**
     * 获取类型安全的 Mapper（泛型参数 MP）。
     *
     * @return 业务方声明的 Mapper 接口实例
     */
    @Override
    public MP getMapper() {
        return (MP) super.getMapper();
    }

}
