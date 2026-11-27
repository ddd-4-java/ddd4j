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

/** \u901a\u8fc7 <code>DataScopeExpressions.eq()</code>\u3001<code>table.getAlias()</code> \u9a8c\u8bc1 <code>Ddd4jDataPermissionBehavior</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class Ddd4jDataPermissionBehaviorTest {

    /**
     * \u9a8c\u8bc1\u6ce8\u518c\u7684\u6570\u636e\u8bbf\u95ee\u8303\u56f4\u540c\u65f6\u7ea6\u675f\u67e5\u8be2\u3001\u8ba1\u6570\u3001\u66f4\u65b0\u548c\u5220\u9664\u3002
     * @throws Exception \u51c6\u5907\u6d4b\u8bd5\u6570\u636e\u5e93\u3001\u542f\u52a8\u6846\u67b6\u6216\u6267\u884c\u4e1a\u52a1\u5408\u540c\u5931\u8d25
     */
    @Test
    void dataScopeConstrainsSelectCountUpdateAndDelete() throws Exception {
        verify(new Ddd4jMybatisConfigurer().dataScope(id -> "dept_id = 10"), "sql_scope");
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>DataScopeExpressions.eq()</code>\u3001<code>table.getAlias()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void structuredPermissionUsesTheSameExecutionChain() throws Exception {
        verify(new Ddd4jMybatisConfigurer().dataPermission(new DataScopeAnnotationHandler(
                (table, where, id, annotation) -> DataScopeExpressions.eq(
                        table.getAlias() == null ? null : table.getAlias().getName(), "dept_id", 10))),
                "structured_scope");
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void rejectsConfigurationOfBothPermissionInputsInEitherOrder() {
        assertThatThrownBy(() -> new Ddd4jMybatisConfigurer().dataScope(id -> null)
                .dataPermission((table, where, id) -> null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new Ddd4jMybatisConfigurer().dataPermission((table, where, id) -> null)
                .dataScope(id -> null)).isInstanceOf(IllegalStateException.class);
    }

    /**
     * \u8c03\u7528 <code>source.setURL()</code>\u3001<code>source.getConnection()</code>\u3001<code>connection.createStatement()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param configurer \u4f20\u7ed9 <code>new Ddd4jMybatisGuiceModule</code> \u7684 <code>Ddd4jMybatisConfigurer</code> \u8f93\u5165
     * @param database \u4f20\u7ed9 <code>source.setURL()</code> \u7684 <code>String</code> \u8f93\u5165
     * @throws Exception \u6267\u884c\u6570\u636e\u5e93\u64cd\u4f5c\u65f6\u5931\u8d25
     */
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

    /** \u63d0\u4f9b <code>visible()</code>\u3001<code>rename()</code>\u3001<code>remove()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    @DataScopePlus
    public interface Rows {
        /**
         * \u6309\u6620\u5c04 SQL \u8bfb\u53d6\u6d4b\u8bd5\u8bb0\u5f55\u3002
         * @param page \u672c\u6b21\u5206\u9875\u67e5\u8be2\u7684\u9875\u7801\u548c\u9875\u5927\u5c0f\u914d\u7f6e
         * @param status \u8981\u7b5b\u9009\u7684\u539f\u59cb\u8bb0\u5f55\u72b6\u6001
         * @return \u4e0e\u67e5\u8be2\u5339\u914d\u7684\u6620\u5c04\u7ed3\u679c
         */
        @Select("SELECT id FROM scope_entry WHERE status = #{status} OR id = 3 ORDER BY id")
        IPage<Integer> visible(Page<Integer> page, @Param("status") int status);

        /**
         * \u6309\u6307\u5b9a\u4e3b\u952e\u66f4\u65b0\u5f53\u524d\u6d4b\u8bd5\u8868\u4e2d\u7684\u8bb0\u5f55\u3002
         * @param id \u88ab\u67e5\u627e\u3001\u5199\u5165\u6216\u5220\u9664\u7684\u5939\u5177\u8bb0\u5f55\u4e3b\u952e
         * @return \u66f4\u65b0\u64cd\u4f5c\u5f71\u54cd\u7684\u884c\u6570
         */
        @Update("UPDATE scope_entry SET status = 3 WHERE id = #{id}")
        int rename(@Param("id") int id);

        /**
         * \u4f9d\u636e\u4e3b\u952e\u6761\u4ef6\u79fb\u9664\u6d4b\u8bd5\u8868\u4e2d\u5339\u914d\u7684\u8bb0\u5f55\u3002
         * @param id \u88ab\u67e5\u627e\u3001\u5199\u5165\u6216\u5220\u9664\u7684\u5939\u5177\u8bb0\u5f55\u4e3b\u952e
         * @return \u5220\u9664\u64cd\u4f5c\u5f71\u54cd\u7684\u884c\u6570
         */
        @Delete("DELETE FROM scope_entry WHERE id = #{id}")
        int remove(@Param("id") int id);
    }
}
