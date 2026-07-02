package io.ddd4j.core.domain.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.ddd4j.core.domain.contract.Page;
import io.ddd4j.core.domain.repository.Repository;
import io.ddd4j.core.domain.repository.RepositoryRegistry;
import io.ddd4j.core.domain.repository.RichRepository;
import io.ddd4j.core.domain.model.AggregateRoot;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.kit.lang.CollKit;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 基础查询对象（充血查询模型）。
 * <p>
 * PO 依赖该对象承载条件查询、分页、排序、聚合等通用读模型参数。
 * 充血查询方法（{@link #one()} / {@link #first()} / {@link #list()} / {@link #page()} /
 * {@link #count()} / {@link #exist()}）通过 {@link RepositoryRegistry} 查找仓储实例，
 * 彻底消除对 MyBatis 等 ORM 的静态注册表耦合。
 *
 * <h3>充血查询示例</h3>
 * <pre>{@code
 * // 查询订单
 * OrderQuery query = new OrderQuery();
 * query.setStatus("PAID");
 * query.current(1).size(20);
 *
 * Page<Order> page = query.page();      // ← 充血分页查询
 * List<Order> list = query.list();      // ← 充血列表查询
 * Order one = query.one();             // ← 充血单条查询
 * boolean exists = query.exist();       // ← 充血存在性查询
 * int count = query.count();           // ← 充血计数查询
 *
 * // 链式调用
 * Page<Order> page = new OrderQuery()
 *     .select("id", "total", "status")
 *     .current(1).size(20)
 *     .orderBy("createTime_DESC")
 *     .page();
 *
 * // 断言查询（查不到就抛异常）
 * Order order = query.one("order.not.found", orderId);  // ← 查不到抛 BizRuntimeException
 * query.exist("order.should.exist", orderId);            // ← 不存在抛异常
 * query.notExist("order.should.not.exist", orderId);     // ← 已存在抛异常
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
@AllArgsConstructor
@NoArgsConstructor
@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class Query<T> extends Page<T> {

    // ========================= 查询条件后缀常量 =========================

    public static final String NOT_QUERY = "Not";
    public static final String NOT_IN_QUERY = "NotIn";
    public static final String IN_QUERY = "In";
    public static final String LIKE_QUERY = "Like";
    public static final String LIKE_LEFT_QUERY = "LikeLeft";
    public static final String LIKE_RIGHT_QUERY = "LikeRight";
    public static final String NOT_LIKE_QUERY = "NotLike";
    public static final String MIN_QUERY = "Min";
    public static final String MAX_QUERY = "Max";
    public static final String MIN_EQUALS_QUERY = "MinEq";
    public static final String MAX_EQUALS_QUERY = "MaxEq";
    public static final String START_QUERY = "Start";
    public static final String END_QUERY = "End";
    public static final String NULL_QUERY = "IsNull";
    public static final String ORS_QUERY = "ors";
    public static final String IN_JSON_QUERY = "InJson";

    public static final List<String> EXCLUDE_FIELDS = Arrays.asList(
            "select", "groupBy", "having", "orderBys", "fields",
            "keyword", "ignoreTenantId", "fills", "ignoreCount");

    // ========================= 查询字段 =========================

    protected String select;
    protected String groupBy;
    protected String having;
    protected String orderBys;
    protected Map<String, Object> ors;
    protected String fields;
    protected Object keyword;
    @ToString.Exclude
    @JsonIgnore
    private boolean ignoreTenantId = false;
    protected String fills;
    @ToString.Exclude
    @JsonIgnore
    protected String split = ",";

    // ========================= 链式构建器 =========================

    public <Q extends Query> Q select(String... columns) {
        if (columns != null) {
            this.setSelect(String.join(split, columns));
        }
        return (Q) this;
    }

    /**
     * 条件 select（模仿 MyBatis-Plus {@code select(boolean condition, ...)} 模式）。
     * <p>
     * 条件为 false 时跳过赋值，消除业务代码中的 if-else 样板。
     *
     * <pre>{@code
     * query.select(StrKit.isNotBlank(fields), fields.split(","))
     * }</pre>
     *
     * @param condition 执行条件
     * @param columns   字段列表
     */
    public <Q extends Query> Q select(boolean condition, String... columns) {
        if (condition && columns != null) {
            this.setSelect(String.join(split, columns));
        }
        return (Q) this;
    }

    public <Q extends Query> Q groupBy(String groupBy) {
        this.setGroupBy(groupBy);
        return (Q) this;
    }

    public <Q extends Query> Q groupBy(boolean condition, String groupBy) {
        if (condition) {
            this.setGroupBy(groupBy);
        }
        return (Q) this;
    }

    public <Q extends Query> Q having(String having) {
        this.setHaving(having);
        return (Q) this;
    }

    public <Q extends Query> Q current(long current) {
        this.setCurrent(current);
        return (Q) this;
    }

    public <Q extends Query> Q size(long size) {
        this.setSize(size);
        return (Q) this;
    }

    /**
     * 条件页大小（模仿 MyBatis-Plus {@code size(boolean condition, ...)} 模式）。
     */
    public <Q extends Query> Q size(boolean condition, long size) {
        if (condition) {
            this.setSize(size);
        }
        return (Q) this;
    }

    public <Q extends Query> Q orderBy(String... orderBys) {
        if (orderBys != null) {
            this.orderBys = String.join(split, orderBys);
        }
        return (Q) this;
    }

    /**
     * 条件排序。
     */
    public <Q extends Query> Q orderBy(boolean condition, String... orderBys) {
        if (condition && orderBys != null) {
            this.orderBys = String.join(split, orderBys);
        }
        return (Q) this;
    }

    public <Q extends Query> Q ors(Object... ors) {
        if (ors != null) {
            if (ors.length % 2 != 0) {
                throw new IllegalArgumentException("ors.length must not be singular");
            }
            Map<String, Object> orsMap = new HashMap<>();
            for (int i = 0; i < ors.length - 1; i += 2) {
                orsMap.put((String) ors[i], ors[i + 1]);
            }
            this.setOrs(orsMap);
        }
        return (Q) this;
    }

    /**
     * 条件 or 查询。
     */
    public <Q extends Query> Q ors(boolean condition, Object... ors) {
        if (condition) {
            return ors(ors);
        }
        return (Q) this;
    }

    public <Q extends Query> Q keyword(String fields, Object keyword) {
        this.setFields(fields);
        this.setKeyword(keyword);
        return (Q) this;
    }

    /**
     * 条件关键字查询。
     */
    public <Q extends Query> Q keyword(boolean condition, String fields, Object keyword) {
        if (condition) {
            this.setFields(fields);
            this.setKeyword(keyword);
        }
        return (Q) this;
    }

    public <Q extends Query> Q ignoreTenantId() {
        this.ignoreTenantId = true;
        return (Q) this;
    }

    public <Q extends Query> Q ignorePage() {
        this.setSize(-1);
        return (Q) this;
    }

    // ========================= 查询前钩子 =========================

    public void with() {
    }

    // ========================= 聚合填充 =========================

    public <Q extends Query> Q fills(String... fills) {
        if (fills != null) {
            if (this.fills != null) {
                this.setFills(this.fills + split + String.join(split, fills));
            } else {
                this.setFills(String.join(split, fills));
            }
        }
        return (Q) this;
    }

    public Boolean fill(String fill) {
        if (this.fills == null || this.fills.isEmpty()) {
            return false;
        }
        List<String> fills = Arrays.asList(this.fills.split(split));
        return fills.contains(fill);
    }

    /**
     * 查询完成后执行聚合填充。
     *
     * @param models 查询结果
     */
    public void doFills(List<? extends AggregateRoot<?>> models) {
        if (models == null || models.isEmpty()) {
            return;
        }
        Repository repo = repository();
        if (repo instanceof RichRepository) {
            ((RichRepository) repo).fill(this, models);
        }
    }

    // ========================= 分页计算 =========================

    @JsonIgnore
    public long getStartIndex() {
        return (getCurrent() - 1L) * getSize();
    }

    @JsonIgnore
    public long getEndIndex() {
        return getStartIndex() + getSize();
    }

    // ========================= 充血查询方法 =========================

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <M extends AggregateRoot<?>> List<M> list() {
        this.with();
        Repository repo = repository();
        if (repo instanceof RichRepository) {
            return ((RichRepository<M, ?>) repo).findList(this);
        }
        throw new BizRuntimeException("Repository for {} does not support list()", this.getClass().getSimpleName());
    }

    public <M extends AggregateRoot<?>> List<M> list(String ifEmpty, Object... params) {
        List<M> list = list();
        if (list == null || list.isEmpty()) {
            throw new BizRuntimeException(ifEmpty, params);
        }
        return list;
    }

    public <M extends AggregateRoot<?>> Page<M> page() {
        this.with();
        Repository repo = repository();
        if (repo instanceof RichRepository) {
            return ((RichRepository<M, ?>) repo).page(this);
        }
        throw new BizRuntimeException("Repository for {} does not support page()", this.getClass().getSimpleName());
    }

    public <M extends AggregateRoot<?>> Page<M> page(String ifEmpty, Object... params) {
        Page<M> p = page();
        if (p == null || p.isEmpty()) {
            throw new BizRuntimeException(ifEmpty, params);
        }
        return p;
    }

    public <M extends AggregateRoot<?>> M one() {
        this.with();
        Repository repo = repository();
        if (repo instanceof RichRepository) {
            return ((RichRepository<M, ?>) repo).findFirst(this).orElse(null);
        }
        throw new BizRuntimeException("Repository for {} does not support one()", this.getClass().getSimpleName());
    }

    /**
     * 获取单个（Optional 版本，模仿 MyBatis-Plus {@code oneOpt()} 模式）。
     *
     * @return Optional 包装的聚合根
     */
    public <M extends AggregateRoot<?>> Optional<M> oneOpt() {
        this.with();
        Repository repo = repository();
        if (repo instanceof RichRepository) {
            return ((RichRepository<M, ?>) repo).findFirst(this);
        }
        throw new BizRuntimeException("Repository for {} does not support oneOpt()", this.getClass().getSimpleName());
    }

    /**
     * 获取首个（Optional 版本）。
     */
    public <M extends AggregateRoot<?>> Optional<M> firstOpt() {
        return oneOpt();
    }

    public <M extends AggregateRoot<?>> M one(String ifNull, Object... params) {
        M one = one();
        if (one == null) {
            throw new BizRuntimeException(ifNull, params);
        }
        return one;
    }

    public <M extends AggregateRoot<?>> M first() {
        return one();
    }

    public <M extends AggregateRoot<?>> M first(String ifNull, Object... params) {
        return one(ifNull, params);
    }

    public long count() {
        this.with();
        Repository repo = repository();
        if (repo instanceof RichRepository) {
            return ((RichRepository<?, ?>) repo).count(this);
        }
        throw new BizRuntimeException("Repository for {} does not support count()", this.getClass().getSimpleName());
    }

    /**
     * 判断数据是否存在（模仿 MyBatis-Plus {@code ChainQuery.exists()} 模式）。
     */
    public boolean exists() {
        return count() > 0;
    }

    public boolean exist() {
        return exists();
    }

    public void exist(String ifNotExist, Object... params) {
        if (!exist()) {
            throw new BizRuntimeException(ifNotExist, params);
        }
    }

    public boolean notExist() {
        return !exist();
    }

    public void notExist(String ifExist, Object... params) {
        if (exist()) {
            throw new BizRuntimeException(ifExist, params);
        }
    }

    // ========================= Map 查询 =========================


    public List<Map<String, Object>> maps() {
        this.with();
        Repository repo = repository();
        if (repo instanceof RichRepository) {
            return ((RichRepository) repo).maps(this);
        }
        throw new BizRuntimeException("Repository for {} does not support maps()", this.getClass().getSimpleName());
    }

    public Map<String, Object> map() {
        List<Map<String, Object>> result = maps();
        if (Objects.isNull(result)) {
            return Collections.emptyMap();
        }
        return result.get(0);
    }

    // ========================= 仓储查找 =========================

    /**
     * 通过 {@link RepositoryRegistry} 查找当前查询类型对应的仓储实例。
     * <p>
     * 子类应绑定到具体的聚合根类型：
     * <pre>{@code
     * public class OrderQuery extends Query {
     *     &#64;Override
     *     protected Repository<Order, ?> repository() {
     *         return RepositoryRegistry.repository(Order.class);
     *     }
     * }
     * }</pre>
     *
     * @return 仓储实例
     */
    protected Repository repository() {
        return RepositoryRegistry.repositoryForQuery(this.getClass());
    }
}
