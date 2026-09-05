package io.ddd4j.data.mybatis.repository.impl;

import java.util.Arrays;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.data.mybatis.repository.MybatisAggregateRepository;

import java.io.Serializable;

/**
 * MyBatis-Plus 仓储实现基类（四泛型，业务方使用入口）。
 *
 * <h3>类继承链</h3>
 * <pre>{@code
 * BaseRepositoryImpl<MP, M, P, Q>
 *   └─ extends MybatisAggregateRepository<MP, M, P, Q, Serializable>   // 充血查询 + 自动填充 + ddd4j Repository
 * }</pre>
 *
 * <h3>完整整合能力</h3>
 * <ul>
 *   <li><b>MyBatis-Plus 抽象基类</b>：{@code AbstractRepository<MP, P>} 提供的批量方法
 *       （{@code saveBatch / updateBatchById / saveOrUpdateBatch}）由父类实现（去 Spring）</li>
 *   <li><b>ddd4j 聚合根仓储</b>：{@link io.ddd4j.core.ddd.repository.Repository}{@code <M, ID>}（继承自父类）</li>
 *   <li><b>ddd4j 充血查询</b>：通过父类构造器自动注册到 RepositoryRegistry</li>
 * </ul>
 *
 * <h3>事务边界</h3>
 * <p>父类的批量方法（{@code saveBatch / updateBatchById / saveOrUpdateBatch}）带
 * {@code @Transactional(rollbackFor = Exception.class)}。调用方（App 层）有事务时自动加入，
 * 无事务时自动新建。
 *
 * <h3>业务方使用</h3>
 * <pre>{@code
 * public class AgentRepositoryImpl
 *         extends BaseRepositoryImpl<AgentMapper, Agent, AgentPO, AgentQuery>
 *         implements AgentRepository {
 * }
 *
 * // 聚合根维度（ddd4j Repository）
 * List<Agent> agents = agentRepository.batchSave(List.of(a1, a2));
 * Optional<Agent> a = agentRepository.findById(id);
 *
 * // PO 维度（继承自父类的批量方法）
 * boolean ok = agentRepository.saveBatch(poList);                      // 高性能批量插入
 * AgentPO po = agentRepository.getById(id);
 *
 * // 充血查询
 * List<Agent> list = new AgentQuery().list();
 * }</pre>
 *
 * @param <MP> MyBatis-Plus Mapper 接口类型
 * @param <M>  聚合根类型（领域模型）
 * @param <P>  持久化对象类型（PO）
 * @param <Q>  查询对象类型（继承 {@link Query}{@code <M>}）
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public abstract class BaseRepositoryImpl<MP extends BaseMapper<P>, M extends AggregateRoot<?>, P, Q extends Query<M>, ID extends Serializable>
        extends MybatisAggregateRepository<MP, M, P, Q, ID> {

    /**
     * 默认构造器：父类构造器已负责 {@code configureTypes} + {@code RepositoryRegistry.register}。
     * 本类无任何额外逻辑，保留无参构造器以满足 Spring / Guice 等 DI 容器要求。
     */
    protected BaseRepositoryImpl() {
        super();
    }
}