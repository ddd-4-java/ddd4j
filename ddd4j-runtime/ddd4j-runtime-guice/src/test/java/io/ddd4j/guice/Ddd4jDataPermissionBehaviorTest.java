package io.ddd4j.guice;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.enhance.annotation.datascope.DataScopePlus;
import com.baomidou.mybatisplus.enhance.datascope.handler.DataScopeAnnotationHandler;
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

/**
 * 通过 <code>DataScopeExpressions.eq()</code>、<code>table.getAlias()</code> 验证 <code>Ddd4jDataPermissionBehavior</code> 的运行行为。
 */
class Ddd4jDataPermissionBehaviorTest {

    /**
     * 验证注册的数据访问范围同时约束查询、计数、更新和删除。
     *
     * @throws Exception 准备测试数据库、启动框架或执行业务合同失败
     */
    @Test
    void dataScopeConstrainsSelectCountUpdateAndDelete() throws Exception {
        verify(new Ddd4jMybatisConfigurer().dataScope(id -> "dept_id = 10"), "sql_scope");
    }

    /**
     * 在测试框架中执行 <code>DataScopeExpressions.eq()</code>、<code>table.getAlias()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void structuredPermissionUsesTheSameExecutionChain() throws Exception {
        verify(new Ddd4jMybatisConfigurer().dataPermission(new DataScopeAnnotationHandler(
                        (table, where, id, annotation) -> DataScopeExpressions.eq(
                                table.getAlias() == null ? null : table.getAlias().getName(), "dept_id", 10))),
                "structured_scope");
    }

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void rejectsConfigurationOfBothPermissionInputsInEitherOrder() {
        assertThatThrownBy(() -> new Ddd4jMybatisConfigurer().dataScope(id -> null)
                .dataPermission((table, where, id) -> null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new Ddd4jMybatisConfigurer().dataPermission((table, where, id) -> null)
                .dataScope(id -> null)).isInstanceOf(IllegalStateException.class);
    }

    /**
     * 调用 <code>source.setURL()</code>、<code>source.getConnection()</code>、<code>connection.createStatement()</code> 执行当前夹具操作。
     *
     * @param configurer 传给 <code>new Ddd4jMybatisGuiceModule</code> 的 <code>Ddd4jMybatisConfigurer</code> 输入
     * @param database   传给 <code>source.setURL()</code> 的 <code>String</code> 输入
     * @throws Exception 执行数据库操作时失败
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

    /**
     * 提供 <code>visible()</code>、<code>rename()</code>、<code>remove()</code> 等供当前测试使用的独立操作。
     */
    @DataScopePlus
    public interface Rows {
        /**
         * 按映射 SQL 读取测试记录。
         *
         * @param page   本次分页查询的页码和页大小配置
         * @param status 要筛选的原始记录状态
         * @return 与查询匹配的映射结果
         */
        @Select("SELECT id FROM scope_entry WHERE status = #{status} OR id = 3 ORDER BY id")
        IPage<Integer> visible(Page<Integer> page, @Param("status") int status);

        /**
         * 按指定主键更新当前测试表中的记录。
         *
         * @param id 被查找、写入或删除的夹具记录主键
         * @return 更新操作影响的行数
         */
        @Update("UPDATE scope_entry SET status = 3 WHERE id = #{id}")
        int rename(@Param("id") int id);

        /**
         * 依据主键条件移除测试表中匹配的记录。
         *
         * @param id 被查找、写入或删除的夹具记录主键
         * @return 删除操作影响的行数
         */
        @Delete("DELETE FROM scope_entry WHERE id = #{id}")
        int remove(@Param("id") int id);
    }
}
