package io.ddd4j.data.mybatis.repository.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import io.ddd4j.core.domain.model.AggregateRoot;
import io.ddd4j.core.domain.query.Query;
import io.ddd4j.data.mybatis.repository.MybatisAggregateRepository;

import java.io.Serializable;

/**
 * MyBatis rich aggregate repository compatibility name.
 *
 * <p>旧 ddd4j 业务代码通常继承四泛型 {@code BaseRepositoryImpl<Mapper, Model, PO, Query>}。
 * 当前类保留这个扩展点，但实现已经收敛到 {@link MybatisAggregateRepository}，
 * 不再依赖旧 {@code Model/BaseRepository} 静态注册轨道。</p>
 *
 * @param <MP> mapper type
 * @param <M>  aggregate root type
 * @param <P>  persistence object type
 * @param <Q>  query object type
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public abstract class BaseRepositoryImpl<MP extends BaseMapper<P>, M extends AggregateRoot<?>, P, Q extends Query>
        extends MybatisAggregateRepository<M, P, Serializable> {

    protected BaseRepositoryImpl() {
        super();
        configureTypes(resolveModelClass(), resolvePersistenceObjectClass(), resolveQueryClass());
    }

    @SuppressWarnings("unchecked")
    private Class<M> resolveModelClass() {
        return (Class<M>) ReflectionKit.getSuperClassGenericType(this.getClass(), BaseRepositoryImpl.class, 1);
    }

    @SuppressWarnings("unchecked")
    private Class<P> resolvePersistenceObjectClass() {
        return (Class<P>) ReflectionKit.getSuperClassGenericType(this.getClass(), BaseRepositoryImpl.class, 2);
    }

    @SuppressWarnings("unchecked")
    private Class<Q> resolveQueryClass() {
        return (Class<Q>) ReflectionKit.getSuperClassGenericType(this.getClass(), BaseRepositoryImpl.class, 3);
    }

    @Override
    @SuppressWarnings("unchecked")
    public MP getMapper() {
        return (MP) super.getMapper();
    }
}
