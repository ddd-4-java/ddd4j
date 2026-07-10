package io.ddd4j.core.ddd.model.metadata;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
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
 * <p><b>零框架依赖</b>：本类只依赖 slf4j（MP 用 ibatis-logging 是其特性，本类用 slf4j 更通用）。
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
@Slf4j
public final class DomainModelHelper {

    private static final Map<Class<?>, DomainModelInfo<?>> MODEL_INFO_CACHE = new ConcurrentHashMap<>();

    private DomainModelHelper() {
    }

    /**
     * 获取 Domain Model 的元数据（带缓存）。
     *
     * @param modelClass                Domain Model 类型
     * @param poClass                   对应的 PO 类型（用于未来扩展，参数签名保留）
     * @param poProperty2ColumnProvider PO 字段名 → DB 列名 的 provider（基础设施层实现）
     * @param <M>                       Domain Model 泛型
     * @return DomainModelInfo 缓存实例
     */
    @SuppressWarnings("unchecked")
    public static <M> DomainModelInfo<M> getModelInfo(Class<M> modelClass,
                                                      Class<?> poClass,
                                                      Function<String, String> poProperty2ColumnProvider) {
        if (modelClass == null || modelClass.isPrimitive() || modelClass.isInterface()) {
            return null;
        }
        DomainModelInfo<?> info = MODEL_INFO_CACHE.get(modelClass);
        if (info != null) {
            return (DomainModelInfo<M>) info;
        }
        synchronized (DomainModelHelper.class) {
            info = MODEL_INFO_CACHE.get(modelClass);
            if (info != null) {
                return (DomainModelInfo<M>) info;
            }
            if (log.isDebugEnabled()) {
                log.debug("init DomainModelInfo for class {} (po={})", modelClass.getName(), poClass);
            }
            info = new DomainModelInfo<>(modelClass, poProperty2ColumnProvider);
            MODEL_INFO_CACHE.put(modelClass, info);
            return (DomainModelInfo<M>) info;
        }
    }

    /**
     * 仅用 Domain Model 构建（PO provider 不传，列名全部走 fallback）。
     */
    public static <M> DomainModelInfo<M> getModelInfo(Class<M> modelClass) {
        return getModelInfo(modelClass, null, null);
    }

    /**
     * 移除 Domain Model 缓存（用于测试或热加载）。
     */
    public static void remove(Class<?> modelClass) {
        MODEL_INFO_CACHE.remove(modelClass);
    }

    /**
     * 清空所有缓存。
     */
    public static void clear() {
        MODEL_INFO_CACHE.clear();
    }
}