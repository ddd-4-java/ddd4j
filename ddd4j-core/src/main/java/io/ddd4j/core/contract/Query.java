package io.ddd4j.core.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.ddd4j.core.contract.exception.ServiceException;
import io.ddd4j.core.util.MappingKit;
import io.ddd4j.kit.lang.JsonKit;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 基础查询对象。
 *
 * <p>PO 依赖该对象承载条件查询、分页、排序、聚合等通用读模型参数。</p>
 * <p>当前已兼容 Lombok 的 {@code @Builder} 与 {@code @Accessors(chain = true)} 场景，
 * Query 子类可按业务需要自由组合上述注解。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
@AllArgsConstructor
@NoArgsConstructor
public abstract class Query extends Page {
    // 查询条件后缀：以该后缀结尾的参数，可以自动补充查询条件
    public static final String NOT_QUERY = "Not";           // !=
    public static final String NOT_IN_QUERY = "NotIn";      // NOT IN（集合或逗号分隔字符串）
    public static final String IN_QUERY = "In";             // IN（集合或逗号分隔字符串）
    public static final String LIKE_QUERY = "Like";         // LIKE '%xxx%'
    public static final String LIKE_LEFT_QUERY = "LikeLeft"; // LIKE 'xxx%'
    public static final String LIKE_RIGHT_QUERY = "LikeRight"; // LIKE '%xxx'
    public static final String NOT_LIKE_QUERY = "NotLike";  // NOT LIKE '%xxx%'
    public static final String MIN_QUERY = "Min";           // >  （数值区间最小值）
    public static final String MAX_QUERY = "Max";           // <  （数值区间最大值）
    public static final String MIN_EQUALS_QUERY = "MinEq";  // >= （数值区间最小值含等）
    public static final String MAX_EQUALS_QUERY = "MaxEq";  // <= （数值区间最大值含等）
    public static final String START_QUERY = "Start";       // >= （开始时间筛选）
    public static final String END_QUERY = "End";           // <= （结束时间/到期时间筛选）
    public static final String NULL_QUERY = "IsNull";       // IS NULL / IS NOT NULL（true=is null）
    public static final String ORS_QUERY = "Ors";           // OR 条件（a=xx or b=yy）
    public static final String IN_JSON_QUERY = "InJson";    // JSON 字段 LIKE 查询
    // 不参与查询的字段
    public static final List<String> EXCLUDE_FIELDS = Arrays.asList("select", "groupBy", "having", "orderBys", "fields",
            "keyword", "ignoreTenantId", "fills", "ignoreCount", "split");
    // select字段列表，多个以split分隔
    protected String select;
    // 分组字段列表
    protected String groupBy;
    // having过滤条件
    protected String having;
    // 排序字段列表，多个以split分隔：aField_DESC,bField_ASC
    protected String orderBys;
    // 原or查询
    protected Map<String, Object> ors;
    // 关键字字段列表，多个以split分隔，配合keyword使用，最终转换成ors查询
    protected String fields;
    // 关键字查询(like '%s%')，配合fields使用
    protected Object keyword;
    // 聚合参数集，多个以split分隔
    protected String fills;
    // 查询分隔符：select/keyword/orderBys/fields/fills，默认英文逗号,
    @ToString.Exclude
    @JsonIgnore
    protected String split = ",";
    // 忽略租户ID查询，默认不忽略
    @ToString.Exclude
    @JsonIgnore
    private boolean ignoreTenantId = false;

    // 通过模型类找到查询类，并把Map参数转换为查询参数
    @SneakyThrows
    public static Query convert(String model, Map<String, Object> queryMap) {
        Class<Query> queryClass = MappingKit.get("MODEL_QUERY", Model.ofName(model));
        if (java.util.Objects.isNull(queryClass)) {
            throw new ServiceException("Query not found");
        }
        // 转两次，为了兼容日期类型的转换，否则String转成Date类型会出错
        Query query = JsonKit.toObject(JsonKit.toJson(queryMap), queryClass);
        if (java.util.Objects.isNull(query)) {
            query = queryClass.getDeclaredConstructor().newInstance();
        }
        return query;
    }

    public <Q extends Query> Q select(String... columns) {
        if (java.util.Objects.nonNull(columns)) {
            this.setSelect(String.join(split, columns));
        }
        return (Q) this;
    }

    public <Q extends Query> Q groupBy(String groupBy) {
        this.setGroupBy(groupBy);
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

    public <Q extends Query> Q orderBy(String... orderBys) {
        if (java.util.Objects.nonNull(orderBys)) {
            this.orderBys = String.join(split, orderBys);
        }
        return (Q) this;
    }

    // or查询
    public <Q extends Query> Q ors(Object... ors) {
        if (java.util.Objects.nonNull(ors)) {
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

    public <Q extends Query> Q keyword(String fields, Object keyword) {
        this.setFields(fields);
        this.setKeyword(keyword);
        return (Q) this;
    }

    // 忽略租户ID查询
    public <Q extends Query> Q ignoreTenantId() {
        this.ignoreTenantId = true;
        return (Q) this;
    }

    // 忽略分页查询
    public <Q extends Query> Q ignorePage() {
        this.setSize(-1);
        return (Q) this;
    }

    // 查询前执行
    public void with() {
    }

    // 聚合哪些数据
    public <Q extends Query> Q fills(String... fills) {
        if (java.util.Objects.nonNull(fills)) {
            if (java.util.Objects.nonNull(this.fills)) {
                this.setFills(this.fills + split + String.join(split, fills));
            } else {
                this.setFills(String.join(split, fills));
            }
        }
        return (Q) this;
    }

    // 是否聚合
    public Boolean fill(String fill) {
        if (java.util.Objects.isNull(this.fills) || this.fills.isEmpty()) {
            return false;
        }
        List<String> fills = Arrays.asList(this.fills.split(split));
        return fills.contains(fill);
    }

    // 执行自定义SQL后再聚合。如果通过当前Query找不到仓库则忽略
    public void doFills(List<Model> models) {
        if (java.util.Objects.nonNull(models) && !models.isEmpty()) {
            try {
                repository().fill(this, models);
            } catch (IllegalArgumentException ignore) {
            }
        }
    }

    @JsonIgnore
    public long getStartIndex() {
        return (getCurrent() - 1L) * getSize();
    }

    @JsonIgnore
    public long getEndIndex() {
        return getStartIndex() + getSize();
    }

    @Deprecated
    public long getPage() {
        return getCurrent();
    }

    @Deprecated
    public void setPage(long page) {
        setCurrent(page);
    }

    @Deprecated
    public long getLimit() {
        return getSize();
    }

    @Deprecated
    public void setLimit(long limit) {
        setSize(limit);
    }

    public <T extends BaseRepository<M, Q>, M extends Model, Q extends Query> T repository() {
        return BaseRepository.of(this.getClass());
    }

    public List<Map<String, Object>> maps() {
        this.with();
        return repository().maps(this);
    }

    public Map<String, Object> map() {
        List<Map<String, Object>> result = maps();
        if (java.util.Objects.isNull(result) || result.isEmpty()) {
            return Collections.emptyMap();
        }
        return result.get(0);
    }

    public <M extends Model> M one() {
        this.with();
        return (M) repository().one(this);
    }

    public <M extends Model> M one(String ifNull, Object... params) {
        M one = one();
        if (java.util.Objects.isNull(one)) {
            log.warn("{}: {}", ifNull, JsonKit.toJson(this));
            throw new ServiceException(ifNull, params);
        }
        return one;
    }

    public <M extends Model> M first() {
        this.with();
        return (M) repository().first(this);
    }

    public <M extends Model> M first(String ifNull, Object... params) {
        M first = first();
        if (java.util.Objects.isNull(first)) {
            log.warn("{}: {}", ifNull, JsonKit.toJson(this));
            throw new ServiceException(ifNull, params);
        }
        return first;
    }

    public int count() {
        this.with();
        return repository().count(this);
    }

    public boolean exist() {
        this.with();
        return repository().exist(this);
    }

    public void exist(String ifNotExist, Object... params) {
        if (!exist()) {
            log.warn("{}: {}", ifNotExist, JsonKit.toJson(this));
            throw new ServiceException(ifNotExist, params);
        }
    }

    public boolean notExist() {
        return !this.exist();
    }

    public void notExist(String ifExist, Object... params) {
        if (exist()) {
            log.warn("{}: {}", ifExist, JsonKit.toJson(this));
            throw new ServiceException(ifExist, params);
        }
    }

    public <M extends Model> Page<M> page() {
        this.with();
        return (Page<M>) repository().page(this);
    }

    public <M extends Model> Page<M> page(String ifEmpty, Object... params) {
        Page<M> page = page();
        if (java.util.Objects.isNull(page) || page.isEmpty()) {
            log.warn("{}: {}", ifEmpty, JsonKit.toJson(this));
            throw new ServiceException(ifEmpty, params);
        }
        return page;
    }

    public <M extends Model> List<M> list() {
        this.with();
        return (List<M>) repository().list(this);
    }

    public <M extends Model> List<M> list(String ifEmpty, Object... params) {
        List<M> list = list();
        if (java.util.Objects.isNull(list) || list.isEmpty()) {
            log.warn("{}: {}", ifEmpty, JsonKit.toJson(this));
            throw new ServiceException(ifEmpty, params);
        }
        return list;
    }

}
