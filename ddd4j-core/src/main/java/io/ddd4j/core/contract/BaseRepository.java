package io.ddd4j.core.contract;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基础仓库接口，业务仓库需要实现当前接口（纯 Java，零 Spring 依赖）
 * <p>
 * 使用静态注册表管理仓库实例。框架适配层通过 {@link #register(Class, BaseRepository)} 注入。
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
public interface BaseRepository<M extends Model, Q extends Query> {
    Map<Class<?>, Class<?>> REPOSITORY_MAPPINGS = new ConcurrentHashMap<>();
    Map<Class<?>, BaseRepository<?, ?>> REPOSITORY_INSTANCES = new ConcurrentHashMap<>();

    /**
     * 注入仓库类（类映射）
     *
     * @param mappingClass    Model类/Query类
     * @param repositoryClass 仓库类
     */
    static <R extends BaseRepository> void inject(Class<?> mappingClass, Class<R> repositoryClass) {
        REPOSITORY_MAPPINGS.put(mappingClass, repositoryClass);
    }

    /**
     * 注册仓库实例（由框架适配层调用）
     *
     * @param mappingClass Model类/Query类
     * @param repository   仓库实例
     */
    static void register(Class<?> mappingClass, BaseRepository<?, ?> repository) {
        REPOSITORY_INSTANCES.put(mappingClass, repository);
    }

    // 获取仓库实例
    @SuppressWarnings("unchecked")
    static <R extends BaseRepository> R of(Class mappingClass) {
        return (R) REPOSITORY_INSTANCES.get(mappingClass);
    }

    /**
     * 获取mapper
     *
     * @return
     * @query <Mapper>
     */
    <MP> MP getMapper();

    /**
     * 保存
     *
     * @return
     * @query model
     */
    boolean save(M model);

    /**
     * 批量保存
     *
     * @return
     * @query models
     */
    boolean save(List<? extends Model> models);

    /**
     * 更新
     *
     * @return
     * @query model
     */
    boolean update(M model);

    /**
     * 自定义更新
     *
     * @return
     * @query model
     * @query query
     */
    boolean update(M model, Q query);

    /**
     * 批量保存
     *
     * @return
     * @query list
     */
    boolean update(List<? extends Model> list);

    /**
     * 保存或更新
     *
     * @return
     * @query model
     */
    boolean saveOrUpdate(M model);

    /**
     * 根据ID删除
     *
     * @return
     * @query id
     */
    boolean delete(Serializable id);

    /**
     * 自定义删除
     *
     * @return
     * @query query
     */
    boolean delete(Q query);

    /**
     * 批量删除
     *
     * @return
     * @query ids
     */
    boolean delete(List<? extends Serializable> ids);

    /**
     * 按@BizKey删除
     *
     * @return
     * @query key
     */
    boolean deleteByKey(Serializable key);

    /**
     * 按@BizKey批量删除
     *
     * @return
     * @query keys
     */
    boolean deleteByKeys(List<Serializable> keys);

    /**
     * 按ID列表查询列表
     *
     * @return
     * @query ids
     */
    List<M> list(List<? extends Serializable> ids);

    /**
     * 按查询参数查询列表
     *
     * @return
     * @query query
     */
    List<M> list(Q query);

    /**
     * 按查询参数查询首行
     *
     * @return
     * @query query
     */
    M first(Q query);

    /**
     * 按查询参数查询一行
     *
     * @return
     * @query query
     */
    M one(Q query);

    /**
     * 按ID查询一行
     *
     * @return
     * @query id
     */
    M get(Serializable id);

    /**
     * 按查询参数查询总行数
     *
     * @return
     * @query query
     */
    int count(Q query);

    /**
     * 按查询参数、返回结果列、分组字段查询
     *
     * @return
     * @query query
     */
    List<Map<String, Object>> maps(Q query);

    /**
     * 按查询参数查询是否存在
     *
     * @return
     * @query query
     */
    boolean exist(Q query);

    /**
     * 按查询参数分页查询
     *
     * @return
     * @query query
     */
    Page<M> page(Q query);

    /**
     * 按@BizKey更新
     *
     * @return
     * @query model
     */
    boolean updateByKey(M model);

    /**
     * 按@BizKey批量更新
     *
     * @return
     * @query models
     */
    boolean updateByKey(List<? extends Model> models);

    /**
     * 按@BizKey查询
     *
     * @return
     * @query key
     */
    M getByKey(String key);

    /**
     * 按@BizKey字段查询列表
     *
     * @return
     * @query keys
     */
    List<M> listByKey(List<Serializable> keys);

    /**
     * 数据聚合填充
     *
     * @query query
     * @query model
     */
    void fill(Q query, M model);

    /**
     * 数据批量聚合填充
     *
     * @query query
     * @query models
     */
    void fill(Q query, List<M> models);

}