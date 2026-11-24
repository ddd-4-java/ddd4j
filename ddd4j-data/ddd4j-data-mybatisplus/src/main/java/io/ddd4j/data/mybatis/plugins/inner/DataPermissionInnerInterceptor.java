package io.ddd4j.data.mybatis.plugins.inner;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import io.ddd4j.data.mybatis.plugins.DataScopeProvider;
import io.ddd4j.kit.lang.StrKit;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;

import java.util.Objects;

/** 将 DDD4J 数据范围策略接入官方多表数据权限解析器。 */
public class DataPermissionInnerInterceptor
        extends com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor {

    /**
     * 创建数据权限拦截器；解析失败时终止操作，避免无权限条件执行。
     * @param provider Mapper 数据范围策略
     */
    public DataPermissionInnerInterceptor(DataScopeProvider provider) {
        super(new ScopeHandler(Objects.requireNonNull(provider, "provider must not be null")));
    }

    private static final class ScopeHandler implements MultiDataPermissionHandler {
        private final DataScopeProvider provider;

        private ScopeHandler(DataScopeProvider provider) {
            this.provider = provider;
        }

        @Override
        public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
            String scope = provider.dataScopeCondition(mappedStatementId);
            if (!StrKit.hasText(scope)) {
                return null;
            }
            try {
                Expression condition = CCJSqlParserUtil.parseCondExpression(scope, false);
                if (condition instanceof OrExpression) {
                    return new ParenthesedExpressionList<>(condition);
                }
                return condition;
            } catch (JSQLParserException exception) {
                throw new IllegalArgumentException("Invalid data scope for " + mappedStatementId, exception);
            }
        }
    }
}
