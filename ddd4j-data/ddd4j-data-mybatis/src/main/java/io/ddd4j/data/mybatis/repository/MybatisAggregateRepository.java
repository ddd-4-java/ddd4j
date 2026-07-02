package io.ddd4j.data.mybatis.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.query.ChainQuery;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.ChainUpdate;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import io.ddd4j.core.domain.model.AggregateRoot;
import io.ddd4j.core.domain.model.DomainObjectMapper;
import io.ddd4j.core.domain.model.DomainObjectMapper;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/**
 * MyBatis-Plus repository base for rich aggregate roots.
 *
 * <p>This class is the MyBatis-specific adapter path. Aggregates remain pure
 * domain models; PO classes and MyBatis-Plus wrappers stay in infrastructure.
 * Business repositories can expose semantic methods while reusing the official
 * MyBatis-Plus chain APIs through protected helpers.</p>
 *
 * @param <M>  aggregate root type
 * @param <P>  persistence object type, usually named {@code *PO}
 * @param <ID> aggregate identity type
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public abstract class MybatisAggregateRepository<M extends AggregateRoot<ID>, P, ID extends Serializable>
        implements DomainRepository<M, ID>, DomainObjectMapper<M, P> {

    private final BaseMapper<P> mapper;

    protected MybatisAggregateRepository(BaseMapper<P> mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    /**
     * Returns the MyBatis-Plus mapper owned by this adapter.
     *
     * @return mapper
     */
    protected BaseMapper<P> mapper() {
        return mapper;
    }

    @Override
    public Optional<M> findById(ID id) {
        if (Objects.isNull(id)) {
            return Optional.empty();
        }
        P persistenceObject = mapper.selectById(id);
        if (Objects.isNull(persistenceObject)) {
            return Optional.empty();
        }
        return Optional.of(toModel(persistenceObject));
    }

    @Override
    public boolean existsById(ID id) {
        return Objects.nonNull(id) && Objects.nonNull(mapper.selectById(id));
    }

    @Override
    public void save(M aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        P persistenceObject = toPersistenceObject(aggregate);
        if (shouldInsert(aggregate)) {
            mapper.insert(persistenceObject);
        } else {
            mapper.updateById(persistenceObject);
        }
    }

    @Override
    public void deleteById(ID id) {
        if (Objects.nonNull(id)) {
            mapper.deleteById(id);
        }
    }

    /**
     * Decides whether the aggregate should be inserted.
     *
     * @param aggregate aggregate root
     * @return {@code true} if an insert should be executed
     */
    protected boolean shouldInsert(M aggregate) {
        ID id = aggregate.id();
        return Objects.isNull(id) || Objects.isNull(mapper.selectById(id));
    }

    /**
     * MyBatis-Plus string-column chain query helper.
     *
     * @return query chain wrapper
     */
    protected QueryChainWrapper<P> query() {
        return new QueryChainWrapper<>(mapper);
    }

    /**
     * MyBatis-Plus lambda chain query helper.
     *
     * @return lambda query chain wrapper
     */
    protected LambdaQueryChainWrapper<P> lambdaQuery() {
        return new LambdaQueryChainWrapper<>(mapper);
    }

    /**
     * MyBatis-Plus string-column chain update helper.
     *
     * @return update chain wrapper
     */
    protected UpdateChainWrapper<P> update() {
        return new UpdateChainWrapper<>(mapper);
    }

    /**
     * MyBatis-Plus lambda chain update helper.
     *
     * @return lambda update chain wrapper
     */
    protected LambdaUpdateChainWrapper<P> lambdaUpdate() {
        return new LambdaUpdateChainWrapper<>(mapper);
    }

    /**
     * ChainQuery view for code that prefers the MyBatis-Plus interface type.
     *
     * @return chain query
     */
    protected ChainQuery<P> chainQuery() {
        return lambdaQuery();
    }

    /**
     * ChainUpdate view for code that prefers the MyBatis-Plus interface type.
     *
     * @return chain update
     */
    protected ChainUpdate<P> chainUpdate() {
        return lambdaUpdate();
    }

    /**
     * MyBatis-Plus wrapper helper for composable lambda queries.
     *
     * @return lambda query wrapper
     */
    protected LambdaQueryWrapper<P> lambdaQueryWrapper() {
        return Wrappers.lambdaQuery();
    }

    /**
     * MyBatis-Plus wrapper helper for composable string-column queries.
     *
     * @return query wrapper
     */
    protected QueryWrapper<P> queryWrapper() {
        return Wrappers.query();
    }

    /**
     * MyBatis-Plus wrapper helper for composable lambda updates.
     *
     * @return lambda update wrapper
     */
    protected LambdaUpdateWrapper<P> lambdaUpdateWrapper() {
        return Wrappers.lambdaUpdate();
    }

    /**
     * MyBatis-Plus wrapper helper for composable string-column updates.
     *
     * @return update wrapper
     */
    protected UpdateWrapper<P> updateWrapper() {
        return Wrappers.update();
    }
}
