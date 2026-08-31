package io.ddd4j.core.ddd.repository;

import io.ddd4j.core.ddd.model.AggregateRoot;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 聚合根类型到仓储实例的纯 Java 注册表。 */
public final class RepositoryRegistry {
    private static final Map<Class<?>, Repository<?, ?>> REPOSITORIES = new ConcurrentHashMap<Class<?>, Repository<?, ?>>();
    private RepositoryRegistry() { }
    public static <M extends AggregateRoot<ID>, ID extends Serializable> void register(Class<M> aggregateType, Repository<M, ID> repository) {
        if (aggregateType == null || repository == null) throw new NullPointerException("aggregateType and repository must not be null");
        REPOSITORIES.put(aggregateType, repository);
    }
    @SuppressWarnings("unchecked") public static <M extends AggregateRoot<ID>, ID extends Serializable> Repository<M, ID> repository(Class<M> aggregateType) {
        Repository<?, ?> repository = REPOSITORIES.get(aggregateType);
        if (repository == null) throw new IllegalStateException("Repository not found for aggregate: " + aggregateType.getName());
        return (Repository<M, ID>) repository;
    }
    public static void unregister(Class<?> aggregateType) { REPOSITORIES.remove(aggregateType); }
}
