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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class DataPermissionInnerInterceptorTest {

    @Test
    void selectWithExistingWhereCombinesBusinessAndDataScopePredicates() {
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
                .beforePrepare(handler, mock(Connection.class), null);

        assertThat(boundSql.getSql()).contains("status = 1", "dept_id IN (3, 4)", "AND");
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
