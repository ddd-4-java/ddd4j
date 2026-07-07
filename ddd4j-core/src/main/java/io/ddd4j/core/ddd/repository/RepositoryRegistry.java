package io.ddd4j.core.ddd.repository;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.exception.BizRuntimeException;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仓储注册表（纯 Java，零 ORM 耦合）。
 * <p>
 * 替代旧 ddd4j 的 {@code BaseRepository.REPOSITORY_INSTANCES} 静态 Map。
 * 通过 {@link BaseContext}（全局默认）+ {@link ThreadContext}（请求级覆盖）
 * 两级上下文查找仓储实例。
 *
 * <h3>框架适配层注册方式</h3>
 * <pre>{@code
 * // Spring 启动期（SpringContextBridge）
 * RepositoryRegistry.register(Order.class, orderRepository);
 *
 * // 或直接通过 BaseContext
 * BaseContext.inject("ddd4j.repository." + Order.class.getName(),
 *                     Repository.class, orderRepository);
 * }</pre>
 *
 * <h3>聚合根充血查找</h3>
 * <pre>{@code
 * // 在 AggregateRoot 内部自动调用
 * Repository<Order, OrderId> repo = RepositoryRegistry.repository(Order.class);
 *
 * // 查找优先级：ThreadContext → BaseContext
 * }</pre>
 *
 * <h3>多租户覆盖</h3>
 * <p>
 * 通过 {@link ThreadContext} 可为当前请求注入租户专属仓储：
 * <pre>{@code
 * // 拦截器：当前请求用 tenantB 的仓储
 * ThreadContext.inject(RepositoryRegistry.key(Order.class), Repository.class, tenantBRepo);
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class RepositoryRegistry {

    /** key 前缀 */
    private static final String PREFIX = "ddd4j.repository.";

    private static final Map<Class<?>, Repository<?, ?>> INSTANCES = new ConcurrentHashMap<>();

    /** Query 类型到仓储实例的轻量映射，支持 query.list()/page()/delete() 默认可用。 */
    private static final Map<Class<?>, Repository<?, ?>> QUERY_INSTANCES = new ConcurrentHashMap<>();

    private RepositoryRegistry() {
    }

    /**
     * 生成 SPI key。
     *
     * @param modelClass 聚合根类型
     * @return key 字符串
     */
    public static String key(Class<?> modelClass) {
        return PREFIX + modelClass.getName();
    }

    /**
     * 注册仓储实例到全局上下文。
     * <p>
     * 通常由框架适配层（Spring/Quarkus/Guice）在启动期调用。
     *
     * @param modelClass 聚合根类型
     * @param repository 仓储实例
     * @param <M>        聚合根类型
     */
    public static <M extends AggregateRoot<?>> void register(
            Class<M> modelClass, Repository<M, ?> repository) {
        BaseContext.inject(key(modelClass), Repository.class, repository);
        INSTANCES.put(modelClass, repository);
    }

    /**
     * 注册聚合根与查询对象到同一个仓储实例。
     *
     * @param modelClass 聚合根类型
     * @param queryClass 查询对象类型
     * @param repository 仓储实例
     * @param <M>        聚合根类型
     */
    public static <M extends AggregateRoot<?>> void register(
            Class<M> modelClass, Class<? extends Query> queryClass, Repository<M, ?> repository) {
        register(modelClass, repository);
        BaseContext.inject(key(queryClass), Repository.class, repository);
        QUERY_INSTANCES.put(queryClass, repository);
    }

    /**
     * 查找聚合根类型的仓储实例。
     * <p>
     * 查找顺序：
     * <ol>
     *   <li>{@link ThreadContext}（线程级，请求级覆盖）</li>
     *   <li>{@link BaseContext}（JVM 级全局默认）</li>
     *   <li>静态 {@link #INSTANCES}（向后兼容）</li>
     * </ol>
     *
     * @param modelClass 聚合根类型
     * @return 仓储实例
     * @throws BizRuntimeException 未找到匹配的仓储
     */
    public static <M extends AggregateRoot<?>> Repository<M, ?> repository(Class<M> modelClass) {
        String spiKey = key(modelClass);

        // 1. 线程级覆盖
        Optional<Repository<M, ?>> threadScoped = ThreadContext.get(spiKey, Repository.class)
                .map(r -> (Repository<M, ?>) r);
        if (threadScoped.isPresent()) {
            return threadScoped.get();
        }

        // 2. 全局默认
        Optional<Repository<M, ?>> globalScoped = BaseContext.get(spiKey, Repository.class)
                .map(r -> (Repository<M, ?>) r);
        if (globalScoped.isPresent()) {
            return globalScoped.get();
        }

        // 3. 静态实例表（向后兼容）
        Repository<?, ?> instance = INSTANCES.get(modelClass);
        if (instance != null) {
            return (Repository<M, ?>) instance;
        }

        throw new BizRuntimeException(
                "Repository not found for aggregate {}. Register it via RepositoryRegistry.register({}, repository) " +
                        "or ensure the framework adapter is configured.",
                modelClass.getSimpleName(), modelClass.getSimpleName());
    }

    /**
     * 根据 Query 类型查找仓储实例。
     *
     * @param queryClass 查询对象类型
     * @return 仓储实例
     */
    public static Repository<?, ?> repositoryForQuery(Class<? extends Query> queryClass) {
        String spiKey = key(queryClass);

        Optional<Repository<?, ?>> threadScoped = ThreadContext.get(spiKey, Repository.class)
                .map(r -> (Repository<?, ?>) r);
        if (threadScoped.isPresent()) {
            return threadScoped.get();
        }

        Optional<Repository<?, ?>> globalScoped = BaseContext.get(spiKey, Repository.class)
                .map(r -> (Repository<?, ?>) r);
        if (globalScoped.isPresent()) {
            return globalScoped.get();
        }

        Repository<?, ?> instance = QUERY_INSTANCES.get(queryClass);
        if (instance != null) {
            return instance;
        }

        throw new BizRuntimeException(
                "Repository not found for query {}. Register it via RepositoryRegistry.register(modelClass, {}, repository) " +
                        "or override Query.repository().",
                queryClass.getSimpleName(), queryClass.getSimpleName());
    }

    /**
     * 移除已注册的仓储实例（用于测试清理）。
     *
     * @param modelClass 聚合根类型
     */
    public static void unregister(Class<?> modelClass) {
        BaseContext.remove(key(modelClass));
        INSTANCES.remove(modelClass);
    }

    /**
     * 移除查询类型映射（用于测试清理）。
     *
     * @param queryClass 查询对象类型
     */
    public static void unregisterQuery(Class<?> queryClass) {
        BaseContext.remove(key(queryClass));
        QUERY_INSTANCES.remove(queryClass);
    }
}
