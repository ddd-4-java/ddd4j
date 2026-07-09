package io.ddd4j.data.mybatis.repository.impl;

import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.repository.Repository;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MyBatis 官方 SqlSession 风格的 Repository 基类（不依赖 MyBatis-Plus）。
 *
 * <p>使用原生 {@link SqlSession} + {@link org.apache.ibatis.annotations.Select}
 * 等注解实现 ddd4j 的充血查询，适配 {@link Repository} SPI。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * &#64;Mapper
 * public interface UserMapper {
 *     &#64;Select("SELECT * FROM user WHERE id = #{id}")
 *     UserPO findByIdPo(&#64;Param("id") String id);
 *
 *     &#64;Select("SELECT * FROM user")
 *     List<UserPO> findAllPo();
 *
 *     &#64;Insert("INSERT INTO user(id, name) VALUES(#{id}, #{name})")
 *     int insertPo(UserPO po);
 * }
 *
 * public class UserRepository
 *         extends MybatisRawAggregateRepository<User, UserPO, String, UserQuery> {
 *     public UserRepository(SqlSession sqlSession) { super(sqlSession, UserMapper.class, User.class, UserPO.class, UserQuery.class); }
 * }
 * }</pre>
 *
 * @param <M>  聚合根类型
 * @param <P>  持久化对象类型（@Mapper 接口返回的 PO）
 * @param <ID> 聚合根标识类型
 * @param <Q>  查询对象类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@Slf4j
@SuppressWarnings("unchecked")
public abstract class MybatisRawAggregateRepository<M extends AggregateRoot<?>, P, ID extends Serializable, Q extends Query>
        implements Repository<M, P, ID> {

    protected final SqlSession sqlSession;
    protected final Class<?> mapperClass;
    protected final Class<M> modelClass;
    protected final Class<P> persistenceClass;
    protected final Class<Q> queryClass;

    protected MybatisRawAggregateRepository(SqlSession sqlSession,
                                          Class<?> mapperClass,
                                          Class<M> modelClass,
                                          Class<P> persistenceClass,
                                          Class<Q> queryClass) {
        this.sqlSession = sqlSession;
        this.mapperClass = mapperClass;
        this.modelClass = modelClass;
        this.persistenceClass = persistenceClass;
        this.queryClass = queryClass;
    }

    // ==========================  Mapper 调用辅助 ==========================

    /**
     * 获取 SqlSession（用于 MyBatis 注解方式查询）。
     */
    protected SqlSession getSqlSession() {
        return sqlSession;
    }

    /**
     * 获取 Mapper 实例（用 SqlSession 反射获取，业务方也可重写）。
     */
    protected <T> T getMapper() {
        return (T) sqlSession.getMapper(mapperClass);
    }

    // ==========================  ddd4j Repository 实现  ==========================

    @Override
    public Optional<M> findById(ID id) {
        try {
            // 约定：mapper 中有 findByIdPo(ID) 方法
            Object po = sqlSession.selectOne(
                mapperClass.getName() + ".findByIdPo", id);
            return po == null ? Optional.empty()
                              : Optional.ofNullable(toModel((P) po));
        } catch (Exception e) {
            // 业务方应确保 mapper 中有 findByIdPo
            log.warn("findById({}) failed: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public M save(M aggregate) {
        // 业务方按需重写此方法（@Insert 注解）
        return aggregate;
    }

    @Override
    public boolean update(AggregateRoot<?> aggregate, Query<P> query) {
        return false;
    }

    @Override
    public boolean deleteByQuery(Query<P> query) {
        return false;
    }

    @Override
    public void fill(Query<P> query, AggregateRoot<?> model) {
        // 业务方按需重写
    }

    @Override
    public Optional<M> findFirst() {
        try {
            Object po = sqlSession.selectOne(
                mapperClass.getName() + ".findFirstPo");
            return po == null ? Optional.empty()
                              : Optional.ofNullable(toModel((P) po));
        } catch (Exception e) {
            log.warn("findFirst failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<M> findAll() {
        List<P> list = sqlSession.selectList(mapperClass.getName() + ".findAllPo");
        return list.stream().map(this::toModel).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public long count() {
        Integer n = sqlSession.selectOne(mapperClass.getName() + ".countAll");
        return n == null ? 0L : n.longValue();
    }

    @Override
    public io.ddd4j.core.api.Page<M> page(Query<P> query) {
        int page = (int) Math.max(0, query.getCurrent() - 1);
        int size = (int) query.getSize();
        List<P> list = sqlSession.selectList(
            mapperClass.getName() + ".findPage", null, new RowBounds(page * size, size));
        List<M> models = list.stream().map(this::toModel).collect(java.util.stream.Collectors.toList());
        return io.ddd4j.core.api.Page.succeed(models, count(), query.getCurrent(), size);
    }

    @Override
    public long count(Query<P> query) {
        return count();
    }

    @Override
    public Optional<M> findFirst(Query<P> query) {
        return findFirst();
    }

    @Override
    public List<M> findList(Query<P> query) {
        return findAll();
    }

    @Override
    public List<Map<String, Object>> maps(Query<P> query) {
        return sqlSession.selectList(mapperClass.getName() + ".findMaps");
    }

    @Override
    public boolean exists() {
        return count() > 0;
    }

    // ==========================  PO ↔ Aggregate 转换  ==========================

    /**
     * PO → Aggregate 转换（业务方按需重写）。
     */
    protected M toModel(P persistenceObject) {
        if (persistenceObject == null) {
            return null;
        }
        return io.ddd4j.kit.lang.BeanKit.copy(persistenceObject, modelClass);
    }

    protected P toPersistenceObject(M model) {
        return (P) io.ddd4j.kit.lang.BeanKit.copy(model, persistenceClass);
    }
}
