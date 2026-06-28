package io.ddd4j.data.mybatis.plugin;

import io.ddd4j.core.contract.Model;
import io.ddd4j.core.contract.Page;
import io.ddd4j.core.contract.Query;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 模型聚合填充插件。
 *
 * <p>MyBatis Executor 拦截器，在查询执行后自动触发数据聚合填充：
 * <ol>
 *   <li>检查 Mapper 方法参数是否包含 {@link Query} 对象</li>
 *   <li>如果是，调用 {@link Query#doFills(List)} 对查询结果进行聚合填充</li>
 *   <li>支持单个 Model、List&lt;Model&gt;、Page&lt;Model&gt; 三种返回类型</li>
 * </ol>
 *
 * <p>使用方式：在 Spring 配置中注册此插件：
 * <pre>
 * &#64;Bean
 * public ModelsFillsPlugin modelsFillsPlugin() {
 *     return new ModelsFillsPlugin();
 * }
 * </pre>
 *
 * <p>或通过 MyBatis-Plus 自动配置：
 * <pre>
 * mybatis-plus:
 *   plugins:
 *     - io.ddd4j.data.mybatis.plugin.ModelsFillsPlugin
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see Query#doFills(List)
 * @since 3.4.x
 */
@Intercepts({@Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})})
public class ModelsFillsPlugin implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object result = invocation.proceed();
        Object parameter = invocation.getArgs()[1];
        // 获取 Mapper 方法的参数
        if (parameter instanceof MapperMethod.ParamMap) {
            for (Object value : ((Map) parameter).values()) {
                if (value instanceof Query) {
                    fills((Query) value, result);
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
        if (result instanceof Model) {
            query.doFills(Collections.singletonList((Model) result));
        } else if (result instanceof List) {
            if (!((List) result).isEmpty() && ((List) result).get(0) instanceof Model) {
                query.doFills((List) result);
            }
        } else if (result instanceof Page) {
            if (!((Page) result).isEmpty() && ((Page) result).getRecords().get(0) instanceof Model) {
                query.doFills(((Page) result).getRecords());
            }
        }
    }

}
