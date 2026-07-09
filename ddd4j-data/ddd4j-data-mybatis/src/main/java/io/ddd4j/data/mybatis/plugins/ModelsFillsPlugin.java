package io.ddd4j.data.mybatis.plugins;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.kit.lang.CollKit;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型聚合填充插件（零 MyBatis-Plus 依赖）。
 *
 * <p>MyBatis Executor 拦截器，在查询执行后自动触发数据聚合填充：
 * <ul>
 *   <li>检查 Mapper 方法参数是否包含 {@link Query} 对象</li>
 *   <li>如果是，调用 {@link Query#doFills(List)} 对查询结果进行聚合填充</li>
 *   <li>支持单个 AggregateRoot、List、Page 三种返回类型</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Intercepts({@Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})})
public class ModelsFillsPlugin implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object result = invocation.proceed();
        Object parameter = invocation.getArgs()[1];
        if (parameter instanceof MapperMethod.ParamMap<?> paramMap) {
            for (Object value : paramMap.values()) {
                if (value instanceof Query query) {
                    fills(query, result);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof Executor) {
            return Plugin.wrap(target, this);
        }
        return target;
    }

    private void fills(Query query, Object result) {
        if (result instanceof AggregateRoot<?> aggregateRoot) {
            query.doFills(Collections.singletonList(aggregateRoot));
        } else if (result instanceof List<?> aggregates) {
            if (CollKit.isNotEmpty(aggregates) && aggregates.get(0) instanceof AggregateRoot<?>) {
                query.doFills(aggregates.stream().map(a -> (AggregateRoot<?>) a).collect(Collectors.toList()));
            }
        } else if (result instanceof Page<?> page) {
            List<?> records = page.getRecords();
            if (CollKit.isNotEmpty(records) && records.get(0) instanceof AggregateRoot<?>) {
                query.doFills(records.stream().map(r -> (AggregateRoot<?>) r).collect(Collectors.toList()));
            }
        }
    }
}
