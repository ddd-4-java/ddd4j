package io.ddd4j.guice;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.enhance.datascope.handler.DataScopeAnnotationHandler;
import com.baomidou.mybatisplus.enhance.annotation.datascope.DataScopePlus;
import com.baomidou.mybatisplus.enhance.datascope.toolkit.DataScopeExpressions;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.ddd4j.data.mybatis.config.Ddd4jMybatisConfigurer;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Ddd4jDataPermissionBehaviorTest {

    @Test
    void dataScopeConstrainsSelectCountUpdateAndDelete() throws Exception {
        verify(new Ddd4jMybatisConfigurer().dataScope(id -> "dept_id = 10"), "sql_scope");
    }

    @Test
    void structuredPermissionUsesTheSameExecutionChain() throws Exception {
        verify(new Ddd4jMybatisConfigurer().dataPermission(new DataScopeAnnotationHandler(
                (table, where, id, annotation) -> DataScopeExpressions.eq(
                        table.getAlias() == null ? null : table.getAlias().getName(), "dept_id", 10))),
                "structured_scope");
    }

    @Test
    void rejectsConfigurationOfBothPermissionInputsInEitherOrder() {
        assertThatThrownBy(() -> new Ddd4jMybatisConfigurer().dataScope(id -> null)
                .dataPermission((table, where, id) -> null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new Ddd4jMybatisConfigurer().dataPermission((table, where, id) -> null)
                .dataScope(id -> null)).isInstanceOf(IllegalStateException.class);
    }

    private void verify(Ddd4jMybatisConfigurer configurer, String database) throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + database);
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE scope_entry(id INT PRIMARY KEY, dept_id INT, status INT)");
            statement.execute("INSERT INTO scope_entry VALUES(1, 10, 1), (2, 20, 1), (3, 10, 2)");
            SqlSessionFactory factory = new Ddd4jMybatisGuiceModule(source)
                    .enhancements(configurer).addMapper(Rows.class).sqlSessionFactory();
            assertThat(factory.getConfiguration().getInterceptors()).hasSize(1);
            try (SqlSession session = factory.openSession(true)) {
                Rows mapper = session.getMapper(Rows.class);
                IPage<Integer> page = mapper.visible(new Page<>(2, 1), 1);
                assertThat(page.getRecords()).containsExactly(3);
                assertThat(page.getTotal()).isEqualTo(2);
                assertThat(mapper.rename(1)).isEqualTo(1);
                assertThat(mapper.rename(2)).isZero();
                assertThat(mapper.remove(2)).isZero();
                assertThat(mapper.remove(3)).isEqualTo(1);
                try (java.sql.ResultSet rows = statement.executeQuery("SELECT status FROM scope_entry WHERE id = 2")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    @DataScopePlus
    public interface Rows {
        @Select("SELECT id FROM scope_entry WHERE status = #{status} OR id = 3 ORDER BY id")
        IPage<Integer> visible(Page<Integer> page, @Param("status") int status);

        @Update("UPDATE scope_entry SET status = 3 WHERE id = #{id}")
        int rename(@Param("id") int id);

        @Delete("DELETE FROM scope_entry WHERE id = #{id}")
        int remove(@Param("id") int id);
    }
}
