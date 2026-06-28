package io.ddd4j.data.mybatis.repository.impl;

import io.ddd4j.core.contract.Model;
import io.ddd4j.core.contract.Query;
import io.ddd4j.core.contract.Repository;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link Repository} 新版 SPI 的 MyBatis 适配器。
 *
 * <p>包装 {@link BaseRepositoryImpl}（旧版 {@code BaseRepository<M,Q>} 实现），
 * 将旧版方法委托转换为新版 {@code Repository<M,Q,P>} 接口方法。
 *
 * <p>由于旧版 {@code BaseRepository} 与新版 {@code Repository} 在 {@code count(Q)} 返回类型
 * （{@code int} vs {@code long}）和 {@code save(M)} 返回类型（{@code boolean} vs {@code M}）
 * 上存在签名冲突，Java 不允许同时 implements 两个接口，因此通过本适配器桥接。
 *
 * @param <M> 实体类型
 * @param <Q> 查询条件类型
 * @param <P> 主键类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
public class MybatisRepositoryAdapter<M extends Model, Q extends Query, P extends Serializable>
        implements Repository<M, Q, P> {

    private final BaseRepositoryImpl<?, M, ?, Q> delegate;

    public MybatisRepositoryAdapter(BaseRepositoryImpl<?, M, ?, Q> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<M> findById(P id) {
        return Optional.ofNullable(delegate.get(id));
    }

    @Override
    public Optional<M> findOne(Q query) {
        return Optional.ofNullable(delegate.one(query));
    }

    @Override
    public List<M> findList(Q query) {
        return delegate.list(query);
    }

    @Override
    public long count(Q query) {
        return delegate.count(query);
    }

    @Override
    public M save(M entity) {
        delegate.save(entity);
        return entity;
    }

    @Override
    public List<M> saveAll(Collection<M> entities) {
        List<? extends Model> list = entities.stream().collect(Collectors.toList());
        delegate.save(list);
        return entities.stream().collect(Collectors.toList());
    }

    @Override
    public M updateById(M entity) {
        delegate.update(entity);
        return entity;
    }

    @Override
    public boolean deleteById(P id) {
        return delegate.delete(id);
    }

    @Override
    public long deleteByIds(Collection<P> ids) {
        List<Serializable> serializableIds = ids.stream()
                .map(id -> (Serializable) id)
                .collect(Collectors.toList());
        return delegate.delete(serializableIds) ? ids.size() : 0;
    }

}
