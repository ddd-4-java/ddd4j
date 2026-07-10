package io.ddd4j.data.mybatis.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import io.ddd4j.core.cqrs.query.Query;

import java.util.Collection;

/**
 * MyBatis-Plus 轨道的 Query 基类（深度整合 LambdaQueryWrapper / LambdaUpdateWrapper）。
 *
 * <p>覆盖 MyBatis-Plus 全部 Lambda 语法，业务方在同一个 Query 对象中即可完成
 * 类型安全的条件构建、排序、分组、更新等操作，然后直接调用 {@code list()}/{@code page()}/{@code one()}
 * 等充血方法执行查询。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class AgentQuery extends AbstractMybatisQuery<Agent> {
 *     protected Repository repository() {
 *         return RepositoryRegistry.repository(Agent.class);
 *     }
 * }
 *
 * // 查询
 * List<Agent> list = new AgentQuery()
 *     .eq(AgentPO::getStatus, 1)
 *     .like(AgentPO::getName, "测试")
 *     .ge(AgentPO::getCreateTime, startTime)
 *     .orderByDesc(AgentPO::getCreateTime)
 *     .list();
 *
 * // 分页
 * Page<Agent> page = new AgentQuery()
 *     .eq(AgentPO::getStatus, 1)
 *     .current(1).size(20)
 *     .page();
 *
 * // 条件构建（消除 if-else 样板）
 * new AgentQuery()
 *     .eq(StrKit.isNotBlank(status), AgentPO::getStatus, status)
 *     .like(StrKit.isNotBlank(keyword), AgentPO::getName, keyword)
 *     .list();
 *
 * // 更新
 * repo.lambdaUpdateChain()
 *     .set(AgentPO::getStatus, "DELETED")
 *     .eq(AgentPO::getStatus, "INACTIVE")
 *     .update();
 * }</pre>
 *
 * @param <T> 聚合根类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public abstract class AbstractMybatisQuery<T> extends Query<T> {

    private static final long serialVersionUID = 1L;

    /**
     * 内部持有的 LambdaQueryWrapper（延迟初始化）
     */
    @SuppressWarnings("rawtypes")
    private transient LambdaQueryWrapper lambdaQueryWrapper;

    // ========================= Wrapper 获取入口 =========================

    /**
     * 获取 MyBatis-Plus 原生 {@link LambdaQueryWrapper}，供 Repository 使用。
     */
    @SuppressWarnings("unchecked")
    public <P> LambdaQueryWrapper<P> getLambdaQueryWrapper() {
        return (LambdaQueryWrapper<P>) lambdaQueryWrapper;
    }

    /**
     * 初始化/获取 {@link LambdaQueryWrapper}（延迟初始化）。
     */
    @SuppressWarnings("unchecked")
    protected <P> LambdaQueryWrapper<P> lambdaQueryWrapper() {
        if (lambdaQueryWrapper == null) {
            lambdaQueryWrapper = Wrappers.lambdaQuery();
        }
        return (LambdaQueryWrapper<P>) lambdaQueryWrapper;
    }

    /**
     * 初始化 {@link QueryWrapper}（字符串字段名风格）。
     */
    protected <P> QueryWrapper<P> queryWrapper() {
        QueryWrapper<P> wrapper = new QueryWrapper<>();
        return wrapper;
    }

    /**
     * 初始化 {@link LambdaUpdateWrapper}（类型安全更新）。
     */
    protected <P> LambdaUpdateWrapper<P> lambdaUpdateWrapper() {
        return Wrappers.lambdaUpdate();
    }

    /**
     * 初始化 {@link UpdateWrapper}（字符串字段名更新）。
     */
    protected <P> UpdateWrapper<P> updateWrapper() {
        return new UpdateWrapper<>();
    }

    // ========================= 等于 / 不等于 =========================

    @Override
    public <Q extends Query<T>> Q eq(io.ddd4j.core.util.SFunction<?, ?> column, Object value) {
        return eq(true, column, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends Query<T>> Q eq(boolean condition, io.ddd4j.core.util.SFunction<?, ?> column, Object value) {
        if (condition && value != null) {
            lambdaQueryWrapper().eq((SFunction) column, value);
        }
        return (Q) this;
    }

    @Override
    public <Q extends Query<T>> Q ne(io.ddd4j.core.util.SFunction<?, ?> column, Object value) {
        return ne(true, column, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends Query<T>> Q ne(boolean condition, io.ddd4j.core.util.SFunction<?, ?> column, Object value) {
        if (condition && value != null) {
            lambdaQueryWrapper().ne((SFunction) column, value);
        }
        return (Q) this;
    }

    // ========================= 模糊匹配 =========================

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends Query<T>> Q like(boolean condition, io.ddd4j.core.util.SFunction<?, ?> column, Object value) {
        if (condition && value != null) {
            lambdaQueryWrapper().like((SFunction) column, value);
        }
        return (Q) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends Query<T>> Q likeLeft(boolean condition, io.ddd4j.core.util.SFunction<?, ?> column, Object value) {
        if (condition && value != null) {
            lambdaQueryWrapper().likeLeft((SFunction) column, value);
        }
        return (Q) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends Query<T>> Q likeRight(boolean condition, io.ddd4j.core.util.SFunction<?, ?> column, Object value) {
        if (condition && value != null) {
            lambdaQueryWrapper().likeRight((SFunction) column, value);
        }
        return (Q) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends Query<T>> Q notLike(boolean condition, io.ddd4j.core.util.SFunction<?, ?> column, Object value) {
        if (condition && value != null) {
            lambdaQueryWrapper().notLike((SFunction) column, value);
        }
        return (Q) this;
    }

    // ========================= 大小比较 =========================

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends Query<T>> Q gt(boolean condition, io.ddd4j.core.util.SFunction<?, ?> column, Object value) {
        if (condition && value != null) {
            lambdaQueryWrapper().gt((SFunction) column, value);
        }
        return (Q) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends Query<T>> Q ge(boolean condition, io.ddd4j.core.util.SFunction<?, ?> column, Object value) {
        if (condition && value != null) {
            lambdaQueryWrapper().ge((SFunction) column, value);
        }
        return (Q) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends Query<T>> Q lt(boolean condition, io.ddd4j.core.util.SFunction<?, ?> column, Object value) {
        if (condition && value != null) {
            lambdaQueryWrapper().lt((SFunction) column, value);
        }
        return (Q) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends Query<T>> Q le(boolean condition, io.ddd4j.core.util.SFunction<?, ?> column, Object value) {
        if (condition && value != null) {
            lambdaQueryWrapper().le((SFunction) column, value);
        }
        return (Q) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends Query<T>> Q between(boolean condition, io.ddd4j.core.util.SFunction<?, ?> column, Object start, Object end) {
        if (condition) {
            lambdaQueryWrapper().between((SFunction) column, start, end);
        }
        return (Q) this;
    }

    // ========================= IN / NOT IN =========================

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends Query<T>> Q in(boolean condition, io.ddd4j.core.util.SFunction<?, ?> column, Collection<?> values) {
        if (condition && values != null && !values.isEmpty()) {
            lambdaQueryWrapper().in((SFunction) column, values);
        }
        return (Q) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends Query<T>> Q notIn(boolean condition, io.ddd4j.core.util.SFunction<?, ?> column, Collection<?> values) {
        if (condition && values != null && !values.isEmpty()) {
            lambdaQueryWrapper().notIn((SFunction) column, values);
        }
        return (Q) this;
    }

    // ========================= NULL 判断 =========================

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends Query<T>> Q isNull(boolean condition, io.ddd4j.core.util.SFunction<?, ?> column) {
        if (condition) {
            lambdaQueryWrapper().isNull((SFunction) column);
        }
        return (Q) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends Query<T>> Q isNotNull(boolean condition, io.ddd4j.core.util.SFunction<?, ?> column) {
        if (condition) {
            lambdaQueryWrapper().isNotNull((SFunction) column);
        }
        return (Q) this;
    }

    // ========================= 排序 =========================

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends Query<T>> Q orderByAsc(boolean condition, io.ddd4j.core.util.SFunction<?, ?> column) {
        if (condition) {
            lambdaQueryWrapper().orderByAsc((SFunction) column);
        }
        return (Q) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends Query<T>> Q orderByDesc(boolean condition, io.ddd4j.core.util.SFunction<?, ?> column) {
        if (condition) {
            lambdaQueryWrapper().orderByDesc((SFunction) column);
        }
        return (Q) this;
    }

    // ========================= SELECT / GROUP BY =========================

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final <Q extends AbstractMybatisQuery<T>> Q selectLambda(boolean condition, io.ddd4j.core.util.SFunction<?, ?>... columns) {
        if (condition && columns != null && columns.length > 0) {
            LambdaQueryWrapper w = lambdaQueryWrapper();
            for (io.ddd4j.core.util.SFunction<?, ?> c : columns) {
                w.select((SFunction) c);
            }
        }
        return (Q) this;
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final <Q extends AbstractMybatisQuery<T>> Q groupByLambda(boolean condition, io.ddd4j.core.util.SFunction<?, ?>... columns) {
        if (condition && columns != null && columns.length > 0) {
            LambdaQueryWrapper w = lambdaQueryWrapper();
            for (io.ddd4j.core.util.SFunction<?, ?> c : columns) {
                w.groupBy((SFunction) c);
            }
        }
        return (Q) this;
    }

    // ========================= MyBatis-Plus 扩展语法（MP 原生 SFunction） =========================
    // 以下方法直接接受 MyBatis-Plus 的 SFunction，方便业务方使用 MP 原生 Lambda

    /**
     * 等于（MP 原生 SFunction）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> eq(SFunction<P, ?> column, Object val) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).eq(column, val);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> eq(boolean condition, SFunction<P, ?> column, Object val) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).eq(condition, column, val);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> ne(SFunction<P, ?> column, Object val) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).ne(column, val);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> gt(SFunction<P, ?> column, Object val) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).gt(column, val);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> ge(SFunction<P, ?> column, Object val) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).ge(column, val);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> lt(SFunction<P, ?> column, Object val) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).lt(column, val);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> le(SFunction<P, ?> column, Object val) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).le(column, val);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> like(SFunction<P, ?> column, Object val) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).like(column, val);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> notLike(SFunction<P, ?> column, Object val) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).notLike(column, val);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> likeLeft(SFunction<P, ?> column, Object val) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).likeLeft(column, val);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> likeRight(SFunction<P, ?> column, Object val) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).likeRight(column, val);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> between(SFunction<P, ?> column, Object val1, Object val2) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).between(column, val1, val2);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> notBetween(SFunction<P, ?> column, Object val1, Object val2) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).notBetween(column, val1, val2);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> in(SFunction<P, ?> column, Collection<?> coll) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).in(column, coll);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> notIn(SFunction<P, ?> column, Collection<?> coll) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).notIn(column, coll);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> inSql(SFunction<P, ?> column, String inValue) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).inSql(column, inValue);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> notInSql(SFunction<P, ?> column, String inValue) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).notInSql(column, inValue);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> isNull(SFunction<P, ?> column) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).isNull(column);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> isNotNull(SFunction<P, ?> column) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).isNotNull(column);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> groupBy(SFunction<P, ?>... columns) {
        LambdaQueryWrapper w = lambdaQueryWrapper();
        for (SFunction c : columns) {
            w.groupBy(c);
        }
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> orderByAsc(SFunction<P, ?>... columns) {
        LambdaQueryWrapper w = lambdaQueryWrapper();
        for (SFunction c : columns) {
            w.orderByAsc(c);
        }
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> orderByDesc(SFunction<P, ?>... columns) {
        LambdaQueryWrapper w = lambdaQueryWrapper();
        for (SFunction c : columns) {
            w.orderByDesc(c);
        }
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> select(SFunction<P, ?>... columns) {
        LambdaQueryWrapper w = lambdaQueryWrapper();
        for (SFunction c : columns) {
            w.select(c);
        }
        return this;
    }

    /**
     * 追加 AND（嵌套条件）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> and(java.util.function.Consumer<LambdaQueryWrapper<P>> consumer) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).and(consumer);
        return this;
    }

    /**
     * 追加 OR（嵌套条件）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> or(java.util.function.Consumer<LambdaQueryWrapper<P>> consumer) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).or(consumer);
        return this;
    }

    /**
     * 追加 OR 连接符。
     */
    @SuppressWarnings("rawtypes")
    public AbstractMybatisQuery<T> or() {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).or();
        return this;
    }

    /**
     * 嵌套条件。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P> AbstractMybatisQuery<T> nested(java.util.function.Consumer<LambdaQueryWrapper<P>> consumer) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).nested(consumer);
        return this;
    }

    /**
     * 拼接 SQL（最后追加）。
     */
    @SuppressWarnings("rawtypes")
    public AbstractMybatisQuery<T> last(String lastSql) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).last(lastSql);
        return this;
    }

    /**
     * 自定义 SQL 条件。
     */
    @SuppressWarnings("rawtypes")
    public AbstractMybatisQuery<T> apply(String applySql, Object... values) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).apply(applySql, values);
        return this;
    }

    /**
     * EXISTS 条件。
     */
    @SuppressWarnings("rawtypes")
    public AbstractMybatisQuery<T> exists(String existsSql) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).exists(existsSql);
        return this;
    }

    /**
     * NOT EXISTS 条件。
     */
    @SuppressWarnings("rawtypes")
    public AbstractMybatisQuery<T> notExists(String notExistsSql) {
        ((LambdaQueryWrapper) lambdaQueryWrapper()).notExists(notExistsSql);
        return this;
    }

    // ========================= 检测方法 =========================

    /**
     * 是否有 MyBatis-Plus 原生 Wrapper 条件。
     */
    public boolean hasManualWrapper() {
        return lambdaQueryWrapper != null;
    }
}
