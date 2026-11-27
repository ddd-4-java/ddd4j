package io.ddd4j.core.ddd.model.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import lombok.extern.slf4j.Slf4j;

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
@SuppressWarnings("unchecked")
public final class DomainModelHelper {

    /** \u6309\u9886\u57df\u548c\u6301\u4e45\u5316\u6a21\u578b\u7c7b\u578b\u7f13\u5b58\u7684\u6620\u5c04\u5143\u6570\u636e\u3002 */
    private static final Map<ModelMappingKey, DomainModelInfo<?>> MODEL_INFO_CACHE = new ConcurrentHashMap<>();

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>DomainModelHelper</code> \u5b9e\u4f8b\u3002 */
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
            if (log.isDebugEnabled()) {
                log.debug("init DomainModelInfo for class {}", modelClass.getName());
            }
            info = new DomainModelInfo<>(modelClass, poProperty2ColumnProvider);
            MODEL_INFO_CACHE.put(key, info);
            return (DomainModelInfo<M>) info;
        }
    }

    /**
     * \u4ec5\u7528 Domain Model \u6784\u5efa\uff08PO provider \u4e0d\u4f20\uff0c\u5217\u540d\u5168\u90e8\u8d70 fallback\uff09\u3002
     *
     * @param <M> \u9886\u57df\u6a21\u578b\u7c7b\u578b
     * @param modelClass \u9886\u57df\u6a21\u578b\u7c7b\u578b
     * @return \u9886\u57df\u6a21\u578b\u5143\u6570\u636e
     */
    public static <M> DomainModelInfo<M> getModelInfo(Class<M> modelClass) {
        return getModelInfo(modelClass, null);
    }

    /**
     * \u79fb\u9664 Domain Model \u7f13\u5b58\uff08\u7528\u4e8e\u6d4b\u8bd5\u6216\u70ed\u52a0\u8f7d\uff09\u3002
     *
     * @param modelClass \u9886\u57df\u6a21\u578b\u7c7b\u578b
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

    /** \u6309\u9886\u57df\u6a21\u578b\u7c7b\u578b\u4e0e\u6301\u4e45\u5316\u6620\u5c04\u7c7b\u578b\u9694\u79bb\u7684\u7f13\u5b58\u952e\u3002 */
    private final static class ModelMappingKey {

        /** Java 序列化版本标识。 */
        private static final long serialVersionUID = 0L;

        /** \u9886\u57df\u6a21\u578b\u7684 Java \u7c7b\u578b\u3002 */
        private final Class<?> modelType;

        /** \u8981\u4e0e\u9886\u57df\u6a21\u578b\u7ec4\u5408\u6620\u5c04\u7684\u6301\u4e45\u5316\u7c7b\u578b\u3002 */
        private final Class<?> persistenceType;

        /**
         * \u7528\u9886\u57df\u548c\u6301\u4e45\u5316\u7c7b\u578b\u6784\u9020\u5143\u6570\u636e\u7f13\u5b58\u952e\u3002
         * @param modelType \u9886\u57df\u6a21\u578b\u7c7b\u578b
         * @param persistenceType \u5bf9\u5e94\u7684\u6301\u4e45\u5316\u7c7b\u578b
         */
        @JsonCreator()
        private ModelMappingKey(@JsonProperty("modelType") Class<?> modelType, @JsonProperty("persistenceType") Class<?> persistenceType) {
            this.modelType = modelType;
            this.persistenceType = persistenceType;
        }

        /**
         * 读取<code>modelType</code> 的当前值。
         *
         * @return 返回表达式 <code>modelType</code>
         */
        @JsonProperty("modelType")
        public Class<?> modelType() {
            return modelType;
        }

        /**
         * 读取<code>persistenceType</code> 的当前值。
         *
         * @return 返回表达式 <code>persistenceType</code>
         */
        @JsonProperty("persistenceType")
        public Class<?> persistenceType() {
            return persistenceType;
        }

        /**
         * 执行 <code>equals</code>，调用 <code>obj.getClass</code>。
         *
         * @param obj 传入此操作的 <code>Object</code> 类型 <code>obj</code>
         * @return 按条件返回 <code>true</code>、<code>false</code>
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            ModelMappingKey other = (ModelMappingKey) obj;
            return Objects.equals(this.modelType, other.modelType) && Objects.equals(this.persistenceType, other.persistenceType);
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(modelType);
            result = 31 * result + Objects.hashCode(persistenceType);
            return result;
        }

        /**
         * 按 <code>to String</code> 的契约计算并返回结果。
         *
         * @return 当前操作得到的 <code>String</code> 结果
         */
        @Override
        public String toString() {
            return "ModelMappingKey[modelType=" + modelType + ", persistenceType=" + persistenceType + "]";
        }
    }
}
