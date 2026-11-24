package io.ddd4j.data.mybatis.plugins.inner;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class DataPermissionInnerInterceptorTest {

    @Test
    void selectWithExistingWhereCombinesBusinessAndDataScopePredicates() throws Exception {
        Configuration configuration = new Configuration();
        BoundSql boundSql = new BoundSql(configuration,
                "SELECT id FROM orders WHERE status = 1", null, null);
        SqlSource sqlSource = parameterObject -> boundSql;
        MappedStatement mappedStatement = new MappedStatement.Builder(
                configuration, "OrderMapper.select", sqlSource, SqlCommandType.SELECT).build();
        StatementHandler handler = mock(StatementHandler.class,
                withSettings().extraInterfaces(HasDelegate.class));
        when(((HasDelegate) handler).getDelegate()).thenReturn(new Delegate(mappedStatement));
        when(handler.getBoundSql()).thenReturn(boundSql);

        new DataPermissionInnerInterceptor(id -> "dept_id IN (3, 4)")
                .beforeQuery(null, mappedStatement, null, org.apache.ibatis.session.RowBounds.DEFAULT, null, boundSql);

        assertThat(boundSql.getSql()).contains("status = 1", "dept_id IN (3, 4)", "AND");
    }

    @Test
    void permissionMustConstrainBothBranchesOfBusinessOr() throws Exception {
        BoundSql sql = rewrite("SELECT id FROM orders WHERE status = 1 OR owner_id = 2", "dept_id = 3");
        net.sf.jsqlparser.statement.select.PlainSelect parsed =
                (net.sf.jsqlparser.statement.select.PlainSelect) net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql.getSql());
        assertThat(parsed.getWhere()).isInstanceOf(net.sf.jsqlparser.expression.operators.conditional.AndExpression.class);
    }

    @Test
    void configuredScopeOrIsGroupedWithTheBusinessCondition() throws Exception {
        BoundSql sql = rewrite("SELECT id FROM orders WHERE status = 1", "dept_id = 3 OR dept_id = 4");
        assertThat(sql.getSql()).contains("status = 1 AND (dept_id = 3 OR dept_id = 4)");
    }

    @Test
    void malformedProtectedSqlMustFailClosed() {
        assertThatThrownBy(() -> rewrite("SELECT id FROM orders WHERE (", "dept_id = 3"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void incompleteDataScopeMustFailClosed() {
        assertThatThrownBy(() -> rewrite("SELECT id FROM orders WHERE status = 1", "dept_id = ("))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid data scope");
    }

    @Test
    void unionMustNotSilentlySkipPermissions() throws Exception {
        BoundSql sql = rewrite("SELECT id FROM orders UNION ALL SELECT id FROM orders", "dept_id = 3");
        assertThat(sql.getSql().split("dept_id", -1)).hasSize(3);
    }

    private BoundSql rewrite(String sql, String scope) throws Exception {
        Configuration configuration = new Configuration();
        BoundSql boundSql = new BoundSql(configuration, sql, null, null);
        MappedStatement statement = new MappedStatement.Builder(configuration, "OrderMapper.select",
                parameter -> boundSql, SqlCommandType.SELECT).build();
        StatementHandler handler = mock(StatementHandler.class, withSettings().extraInterfaces(HasDelegate.class));
        when(((HasDelegate) handler).getDelegate()).thenReturn(new Delegate(statement));
        when(handler.getBoundSql()).thenReturn(boundSql);
        new DataPermissionInnerInterceptor(id -> scope).beforeQuery(null, statement, null, org.apache.ibatis.session.RowBounds.DEFAULT, null, boundSql);
        return boundSql;
    }

    public interface HasDelegate {
        Delegate getDelegate();
    }

    public static final class Delegate {
        private final MappedStatement mappedStatement;

        public Delegate(MappedStatement mappedStatement) {
            this.mappedStatement = mappedStatement;
        }

        public MappedStatement getMappedStatement() {
            return mappedStatement;
        }
    }
}
