package io.ddd4j.core.ddd.model.metadata;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Domain Model 元数据缓存助手（仿 MyBatis-Plus {@code TableInfoHelper}）。
 *
 * <p>充血查询链路中，业务方用 Domain Model 字段引用 Lambda，本类缓存
 * {@link DomainModelInfo}（包含字段→PO 列名映射）以加速翻译。
 *
 * <h3>与 {@code TableInfoHelper} 的对偶关系</h3>
 * <table border="1">
 *   <tr><th>维度</th><th>PO（MP 原生）</th><th>Domain（本类）</th></tr>
 *   <tr><td>元数据</td><td>{@code TableInfo}</td><td>{@code DomainModelInfo}</td></tr>
 *   <tr><td>字段</td><td>{@code TableFieldInfo}</td><td>{@code DomainFieldInfo}</td></tr>
 *   <tr><td>缓存助手</td><td>{@code TableInfoHelper}</td><td>{@code DomainModelHelper}</td></tr>
 *   <tr><td>property</td><td>PO 字段名</td><td>Domain 字段名</td></tr>
 *   <tr><td>column</td><td>DB 列名</td><td>DB 列名（来自 PO TableInfo 桥接）</td></tr>
 * </table>
 *
 * <p><b>零依赖</b>：本类为纯 JDK 实现（ADR-0002：core 不直接依赖日志门面），
 * 无 MP 的 ibatis-logging 依赖。
 * PO 字段→列名映射通过 {@code poProperty2ColumnProvider} 由基础设施层注入。
 *
 * <h3>充血查询翻译链路</h3>
 * <pre>{@code
 * // Domain 层（业务方）
 * new UserQuery().eq(User::getUserName, "alice");
 *
 * // Infrastructure 层
 * DomainModelInfo<User> info = DomainModelHelper.getModelInfo(User.class, UserPO.class,
 *         poProperty -> poTableInfo.getFieldMap().get(poProperty).getColumn());
 * String column = info.getPoColumn("userName");  // → "user_name"
 * wrapper.eq(column, "alice");
 * }</pre>
 *
 * @author wandl
 * @since 2.0.x
 */
@SuppressWarnings("unchecked")
public final class DomainModelHelper {

    private static final Map<ModelMappingKey, DomainModelInfo<?>> MODEL_INFO_CACHE = new ConcurrentHashMap<>();

    private DomainModelHelper() {
    }

    /**
     * 获取 Domain Model 的元数据（带缓存）。
     *
     * @param modelClass                Domain Model 类型
     * @param poProperty2ColumnProvider PO 字段名 → DB 列名 的 provider（基础设施层实现）
     * @param <M>                       Domain Model 泛型
     * @return DomainModelInfo 缓存实例
     */
    public static <M> DomainModelInfo<M> getModelInfo(Class<M> modelClass,
                                                      Function<String, String> poProperty2ColumnProvider) {
        return getModelInfo(modelClass, modelClass, poProperty2ColumnProvider);
    }

    /**
     * 获取指定持久化映射上下文的 Domain Model 元数据。
     *
     * <p>缓存键同时包含领域模型和持久化类型，避免同一领域模型映射到多个
     * PO 或 ORM 适配器时由第一次初始化结果污染后续映射。
     *
     * @param modelClass                Domain Model 类型
     * @param persistenceType           持久化对象或映射上下文类型
     * @param poProperty2ColumnProvider PO 字段名到数据库列名的映射
     * @param <M>                       Domain Model 泛型
     * @return DomainModelInfo 缓存实例
     */
    public static <M> DomainModelInfo<M> getModelInfo(Class<M> modelClass,
                                                      Class<?> persistenceType,
                                                      Function<String, String> poProperty2ColumnProvider) {
        if (Objects.isNull(modelClass) || modelClass.isPrimitive() || modelClass.isInterface()) {
            return null;
        }
        Class<?> mappingType = Objects.requireNonNull(persistenceType, "persistenceType must not be null");
        ModelMappingKey key = new ModelMappingKey(modelClass, mappingType);
        DomainModelInfo<?> info = MODEL_INFO_CACHE.get(key);
        if (Objects.nonNull(info)) {
            return (DomainModelInfo<M>) info;
        }
        synchronized (DomainModelHelper.class) {
            info = MODEL_INFO_CACHE.get(key);
            if (Objects.nonNull(info)) {
                return (DomainModelInfo<M>) info;
            }
            info = new DomainModelInfo<>(modelClass, poProperty2ColumnProvider);
            MODEL_INFO_CACHE.put(key, info);
            return (DomainModelInfo<M>) info;
        }
    }

    /**
     * 仅用 Domain Model 构建（PO provider 不传，列名全部走 fallback）。
     */
    public static <M> DomainModelInfo<M> getModelInfo(Class<M> modelClass) {
        return getModelInfo(modelClass, null);
    }

    /**
     * 移除 Domain Model 缓存（用于测试或热加载）。
     */
    public static void remove(Class<?> modelClass) {
        MODEL_INFO_CACHE.keySet().removeIf(key -> Objects.equals(key.modelType(), modelClass));
    }

    /**
     * 清空所有缓存。
     */
    public static void clear() {
        MODEL_INFO_CACHE.clear();
    }

    private record ModelMappingKey(Class<?> modelType, Class<?> persistenceType) {
    }
}
