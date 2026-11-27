package io.ddd4j.data.mybatis.repository;

import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.enhance.mapper.EnhanceMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 通过 <code>source.setURL()</code>、<code>source.getConnection()</code>、<code>connection.createStatement()</code> 验证 <code>NativeQuerySql</code> 的运行行为。
 */
class NativeQuerySqlTest {
    /**
     * 验证<code>repository.count(query)</code> 等于 <code>2</code>，<code>page.getTotal()</code> 等于 <code>2</code>，<code>(Object) io.ddd4j.core.context.ThreadContext.get(io.ddd4j.core.constant.ContextConstants.PREPARING_SQL)</code>非空。
     *
     * @throws Exception 反射访问被测成员、执行数据库操作时失败
     */
    @Test
    void queryPageCountAndDeleteMustRunInDatabase() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:native_query;DB_CLOSE_DELAY=-1");
        try (java.sql.Connection connection = source.getConnection(); java.sql.Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS native_entry(id INT PRIMARY KEY)");
            statement.execute("DELETE FROM native_entry");
            statement.execute("INSERT INTO native_entry VALUES (1), (2), (3)");
        }
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setEnvironment(new org.apache.ibatis.mapping.Environment("native", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), source));
        configuration.addMapper(EntryMapper.class);
        Class<?> configurer = Class.forName("io.ddd4j.data.mybatis.config.Ddd4jMybatisConfigurer");
        org.apache.ibatis.plugin.Interceptor chain = (org.apache.ibatis.plugin.Interceptor) configurer.getMethod("build")
                .invoke(configurer.getConstructor().newInstance());
        configuration.addInterceptor(chain);
        SqlSessionFactory factory = new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = factory.openSession(true)) {
            TestRepository repository = new TestRepository(session.getMapper(EntryMapper.class));
            Query<Entry> query = new Query<Entry>() {
            };
            query.gt(Entry::getId, 1);
            query.setCurrent(1);
            query.setSize(1);
            assertThat(repository.count(query)).isEqualTo(2);
            query.setCurrent(2);
            var page = repository.page(query);
            assertThat(page.getTotal()).isEqualTo(2);
            assertThat(page.getRecords()).extracting(Entry::getId).containsExactly(3);
            assertThat(repository.findFirst(query)).get().extracting(Entry::getId).isEqualTo(2);
            assertThat(repository.findFirst()).get().extracting(Entry::getId).isEqualTo(1);
            assertThat(repository.findList(query)).extracting(Entry::getId).containsExactly(2, 3);
            Query<Entry> unsupported = new Query<Entry>() {
            };
            unsupported.gt(Entry::getId, 0).eq(Entry::getId, 1);
            assertThatThrownBy(() -> repository.count(unsupported)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> repository.deleteByQuery(unsupported)).isInstanceOf(UnsupportedOperationException.class);
            assertThat(repository.count()).isEqualTo(3);
            repository.deleteById(2);
            assertThat(repository.count()).isEqualTo(2);
            assertThat(repository.deleteByIds(List.of(1, 3))).isEqualTo(2);
            assertThat(repository.count()).isZero();
            assertThat((Object) io.ddd4j.core.context.ThreadContext.get(io.ddd4j.core.constant.ContextConstants.PREPARING_SQL)).isNotNull();
        }
    }

    /**
     * 验证<code>mapper.find(1)</code> 等于 <code>"a"</code>，<code>mapper.find(2)</code>为空，<code>mapper.find(2)</code> 抛出 <code>org.apache.ibatis.exceptions.PersistenceException.class</code>。
     *
     * @throws Exception 反射访问被测成员、执行数据库操作时失败
     */
    @Test
    void tenantMustConstrainDirectMapperReadWriteAndContextSwitch() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:native_tenant;DB_CLOSE_DELAY=-1");
        try (java.sql.Connection connection = source.getConnection(); java.sql.Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE native_tenant(id INT PRIMARY KEY, tenant_id VARCHAR(40))");
            statement.execute("INSERT INTO native_tenant VALUES(1, 'a'), (2, 'b')");
        }
        Class<?> type = Class.forName("io.ddd4j.data.mybatis.config.Ddd4jMybatisConfigurer");
        Object configurer = type.getConstructor().newInstance();
        type.getMethod("tenant", String.class, java.util.function.Predicate.class)
                .invoke(configurer, "tenant_id", (java.util.function.Predicate<String>) table -> false);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setEnvironment(new org.apache.ibatis.mapping.Environment("native", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), source));
        configuration.addInterceptor((org.apache.ibatis.plugin.Interceptor) type.getMethod("build").invoke(configurer));
        configuration.addMapper(TenantMapper.class);
        try (SqlSession session = new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration).openSession(true)) {
            TenantMapper mapper = session.getMapper(TenantMapper.class);
            io.ddd4j.core.context.ThreadContext.set(io.ddd4j.core.constant.ContextConstants.TENANT_ID, "a");
            assertThat(mapper.find(1)).isEqualTo("a");
            assertThat(mapper.find(2)).isNull();
            assertThat(mapper.remove(2)).isZero();
            io.ddd4j.core.context.ThreadContext.set(io.ddd4j.core.constant.ContextConstants.TENANT_ID, "b");
            assertThat(mapper.find(2)).isEqualTo("b");
            assertThat(mapper.find(1)).isNull();
            assertThat(mapper.insert(3, "b")).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> mapper.insert(4, "a"))
                    .isInstanceOf(org.apache.ibatis.exceptions.PersistenceException.class);
            io.ddd4j.core.context.ThreadContext.clear();
            session.clearCache();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> mapper.find(2))
                    .isInstanceOf(org.apache.ibatis.exceptions.PersistenceException.class);
        } finally {
            io.ddd4j.core.context.ThreadContext.clear();
        }
    }

    /**
     * 提供 <code>find()</code>、<code>remove()</code>、<code>insert()</code> 等供当前测试使用的独立操作。
     */
    interface TenantMapper {
        /**
         * 按映射 SQL 读取测试记录并返回租户列。
         *
         * @param id 被查找、写入或删除的夹具记录主键
         * @return 与查询匹配的映射结果
         */
        @Select("SELECT tenant_id FROM native_tenant WHERE id = #{id}")
        String find(int id);

        /**
         * 依据主键条件移除测试表中匹配的记录。
         *
         * @param id 被查找、写入或删除的夹具记录主键
         * @return 删除操作影响的行数
         */
        @Delete("DELETE FROM native_tenant WHERE id = #{id}")
        int remove(int id);

        /**
         * 把给定夹具数据插入当前测试表。
         *
         * @param id     被查找、写入或删除的夹具记录主键
         * @param tenant 当前测试记录所属的租户
         * @return 插入操作影响的行数
         */
        @org.apache.ibatis.annotations.Insert("INSERT INTO native_tenant(id, tenant_id) VALUES(#{id}, #{tenant})")
        int insert(@Param("id") int id, @Param("tenant") String tenant);
    }

    /**
     * 提供 MyBatis 增强 Mapper 的测试接口，供仓储或映射契约用例实例化。
     */
    interface EntryMapper extends EnhanceMapper<Entry> {
        /**
         * 测试 SQL 共用的筛选语句片段。
         */
        String FILTER = " FROM native_entry <if test='min != null'>WHERE id &gt; #{min}</if>";

        /**
         * 构造 <code>AssertionError</code> 作为当前测试的输入或结果。
         *
         * @return 该异常路径没有正常返回结果
         */
        @Override
        default List<Entry> selectList() {
            throw new AssertionError("query must not load all rows");
        }

        /**
         * 按映射 SQL 读取测试记录。
         *
         * @param min    查询允许返回的最小业务值
         * @param offset 跳过的记录数量
         * @param limit  最多返回的记录数量，负值表示无分页限制
         * @return 与查询匹配的映射结果
         */
        @Select("<script>SELECT id" + FILTER + " ORDER BY id <if test='limit &gt;= 0'>LIMIT #{limit} OFFSET #{offset}</if></script>")
        List<Entry> selectQuery(@Param("min") Integer min, @Param("offset") long offset, @Param("limit") long limit);

        /**
         * 统计当前测试表中符合条件的记录数。
         *
         * @param min 查询允许返回的最小业务值
         * @return 满足映射查询条件的行数
         */
        @Select("<script>SELECT COUNT(*)" + FILTER + "</script>")
        long countQuery(@Param("min") Integer min);

        /**
         * 依据主键条件移除测试表中匹配的记录。
         *
         * @param id 被查找、写入或删除的夹具记录主键
         * @return 删除操作影响的行数
         */
        @Delete("DELETE FROM native_entry WHERE id = #{id}")
        int deleteById(Integer id);

        /**
         * 依据主键条件移除测试表中匹配的记录。
         *
         * @param ids 当前批量删除的测试主键集合
         * @return 删除操作影响的行数
         */
        @Delete("<script>DELETE FROM native_entry WHERE id IN <foreach collection='ids' item='id' open='(' close=')' separator=','>#{id}</foreach></script>")
        int deleteByIds(@Param("ids") Collection<Integer> ids);
    }

    /**
     * 供测试验证事件应用和持久化的聚合样本。
     */
    public static class Entry extends AggregateRoot<Integer> {
        /**
         * 用于构造和定位测试记录的主键。
         */
        private Integer id;

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        @Override
        public Integer id() {
            return id;
        }

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        public Integer getId() {
            return id;
        }

        /**
         * 设置<code>id</code> 字段。
         *
         * @param id 要写入的值
         */
        public void setId(Integer id) {
            this.id = id;
        }
    }

    /**
     * 实现 <code>MybatisAggregateRepository</code>，供测试调用 <code>selectRows()</code>、<code>countRows()</code>、<code>deleteRow()</code> 并观察结果。
     */
    static class TestRepository extends MybatisAggregateRepository<EntryMapper, Entry, Entry, Query<Entry>, Integer> {
        /**
         * 使用 <code>super()</code> 初始化 <code>TestRepository</code> 测试夹具。
         *
         * @param mapper 当前测试登记或使用的持久化映射组件
         */
        TestRepository(EntryMapper mapper) {
            super(mapper, Entry.class, Entry.class);
        }

        /**
         * 查询符合条件的持久化记录。
         *
         * <p>测试夹具会调用 <code>baseMapper.selectQuery(minimumId(query), offset, limit)</code>、<code>minimumId(query)</code>。</p>
         *
         * @param query  领域查询，null 表示无额外条件
         * @param offset 从零开始的偏移量
         * @param limit  最大行数，负数表示不分页
         * @return 实际返回<code>baseMapper.selectQuery(minimumId(query), offset, limit)</code>
         */
        @Override
        protected List<Entry> selectRows(Query<Entry> query, long offset, long limit) {
            return baseMapper.selectQuery(minimumId(query), offset, limit);
        }

        /**
         * 统计符合条件的持久化记录数。
         *
         * <p>测试夹具会调用 <code>baseMapper.countQuery(minimumId(query))</code>、<code>minimumId(query)</code>。</p>
         *
         * @param query 领域查询，null 表示无额外条件
         * @return 实际返回<code>baseMapper.countQuery(minimumId(query))</code>
         */
        @Override
        protected long countRows(Query<Entry> query) {
            return baseMapper.countQuery(minimumId(query));
        }

        /**
         * 通过 <code>baseMapper.deleteById()</code> 取得当前测试需要的结果。
         *
         * @param id 当前要查找的测试记录标识
         * @return 当前测试使用的结果，取自 <code>baseMapper.deleteById(id)</code>
         */
        @Override
        protected int deleteRow(Integer id) {
            return baseMapper.deleteById(id);
        }

        /**
         * 删除符合条件的持久化记录。
         *
         * <p>测试夹具会调用 <code>baseMapper.deleteByIds(ids)</code>。</p>
         *
         * @param ids 非空主键集合
         * @return 实际返回<code>baseMapper.deleteByIds(ids)</code>
         */
        @Override
        protected int deleteRows(Collection<Integer> ids) {
            return baseMapper.deleteByIds(ids);
        }

        // 本示例的 SQL 仅接受可选的 id > min；在查询前拒绝其他条件。

        /**
         * 按 <code>(Objects.isNull(query))</code> 等实际条件选择当前用例返回的结果。
         *
         * @param query 用来读取测试数据的查询对象
         * @return 根据测试条件返回的可能值，包括 <code>null</code>、<code>minimum</code>
         */
        private Integer minimumId(Query<Entry> query) {
            if (Objects.isNull(query)) {
                return null;
            }
            if (query.hasOrderBy() || query.hasGroupBy() || query.hasSelect() || query.hasSet()
                    || StringUtils.isNotBlank(query.getHaving()) || query.isIgnoreTenantId()) {
                throw new UnsupportedOperationException("Unsupported entry query");
            }
            var conditions = query.getWhereConditions();
            if (conditions.isEmpty()) {
                return null;
            }
            var condition = conditions.get(0);
            if (conditions.size() == 1 && "id".equals(condition.property()) && ">".equals(condition.operator())
                    && condition.value() instanceof Integer minimum) {
                return minimum;
            }
            throw new UnsupportedOperationException("Expected a single integer id lower bound");
        }
    }
}
