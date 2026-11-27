package io.ddd4j.data.mybatis.repository;

import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.apache.ibatis.enhance.mapper.EnhanceMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import java.util.List;
import java.util.Collection;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

/** \u901a\u8fc7 <code>source.setURL()</code>\u3001<code>source.getConnection()</code>\u3001<code>connection.createStatement()</code> \u9a8c\u8bc1 <code>NativeQuerySql</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class NativeQuerySqlTest {
    /**
     * \u9a8c\u8bc1<code>repository.count(query)</code> \u7b49\u4e8e <code>2</code>\uff0c<code>page.getTotal()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>(Object) io.ddd4j.core.context.ThreadContext.get(io.ddd4j.core.constant.ContextConstants.PREPARING_SQL)</code>\u975e\u7a7a\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u3001\u6267\u884c\u6570\u636e\u5e93\u64cd\u4f5c\u65f6\u5931\u8d25
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
            Query<Entry> query = new Query<Entry>() {};
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
            Query<Entry> unsupported = new Query<Entry>() {};
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
     * \u9a8c\u8bc1<code>mapper.find(1)</code> \u7b49\u4e8e <code>"a"</code>\uff0c<code>mapper.find(2)</code>\u4e3a\u7a7a\uff0c<code>mapper.find(2)</code> \u629b\u51fa <code>org.apache.ibatis.exceptions.PersistenceException.class</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u3001\u6267\u884c\u6570\u636e\u5e93\u64cd\u4f5c\u65f6\u5931\u8d25
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

    /** \u63d0\u4f9b <code>find()</code>\u3001<code>remove()</code>\u3001<code>insert()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    interface TenantMapper {
        /**
         * \u6309\u6620\u5c04 SQL \u8bfb\u53d6\u6d4b\u8bd5\u8bb0\u5f55\u5e76\u8fd4\u56de\u79df\u6237\u5217\u3002
         * @param id \u88ab\u67e5\u627e\u3001\u5199\u5165\u6216\u5220\u9664\u7684\u5939\u5177\u8bb0\u5f55\u4e3b\u952e
         * @return \u4e0e\u67e5\u8be2\u5339\u914d\u7684\u6620\u5c04\u7ed3\u679c
         */
        @Select("SELECT tenant_id FROM native_tenant WHERE id = #{id}")
        String find(int id);
        /**
         * \u4f9d\u636e\u4e3b\u952e\u6761\u4ef6\u79fb\u9664\u6d4b\u8bd5\u8868\u4e2d\u5339\u914d\u7684\u8bb0\u5f55\u3002
         * @param id \u88ab\u67e5\u627e\u3001\u5199\u5165\u6216\u5220\u9664\u7684\u5939\u5177\u8bb0\u5f55\u4e3b\u952e
         * @return \u5220\u9664\u64cd\u4f5c\u5f71\u54cd\u7684\u884c\u6570
         */
        @Delete("DELETE FROM native_tenant WHERE id = #{id}")
        int remove(int id);
        /**
         * \u628a\u7ed9\u5b9a\u5939\u5177\u6570\u636e\u63d2\u5165\u5f53\u524d\u6d4b\u8bd5\u8868\u3002
         * @param id \u88ab\u67e5\u627e\u3001\u5199\u5165\u6216\u5220\u9664\u7684\u5939\u5177\u8bb0\u5f55\u4e3b\u952e
         * @param tenant \u5f53\u524d\u6d4b\u8bd5\u8bb0\u5f55\u6240\u5c5e\u7684\u79df\u6237
         * @return \u63d2\u5165\u64cd\u4f5c\u5f71\u54cd\u7684\u884c\u6570
         */
        @org.apache.ibatis.annotations.Insert("INSERT INTO native_tenant(id, tenant_id) VALUES(#{id}, #{tenant})")
        int insert(@Param("id") int id, @Param("tenant") String tenant);
    }

    /** \u63d0\u4f9b MyBatis \u589e\u5f3a Mapper \u7684\u6d4b\u8bd5\u63a5\u53e3\uff0c\u4f9b\u4ed3\u50a8\u6216\u6620\u5c04\u5951\u7ea6\u7528\u4f8b\u5b9e\u4f8b\u5316\u3002 */
    interface EntryMapper extends EnhanceMapper<Entry> {
        /**
         * \u6784\u9020 <code>AssertionError</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
         * @return \u8be5\u5f02\u5e38\u8def\u5f84\u6ca1\u6709\u6b63\u5e38\u8fd4\u56de\u7ed3\u679c
         */
        @Override
        default List<Entry> selectList() { throw new AssertionError("query must not load all rows"); }
        /** \u6d4b\u8bd5 SQL \u5171\u7528\u7684\u7b5b\u9009\u8bed\u53e5\u7247\u6bb5\u3002 */
        String FILTER = " FROM native_entry <if test='min != null'>WHERE id &gt; #{min}</if>";
        /**
         * \u6309\u6620\u5c04 SQL \u8bfb\u53d6\u6d4b\u8bd5\u8bb0\u5f55\u3002
         * @param min \u67e5\u8be2\u5141\u8bb8\u8fd4\u56de\u7684\u6700\u5c0f\u4e1a\u52a1\u503c
         * @param offset \u8df3\u8fc7\u7684\u8bb0\u5f55\u6570\u91cf
         * @param limit \u6700\u591a\u8fd4\u56de\u7684\u8bb0\u5f55\u6570\u91cf\uff0c\u8d1f\u503c\u8868\u793a\u65e0\u5206\u9875\u9650\u5236
         * @return \u4e0e\u67e5\u8be2\u5339\u914d\u7684\u6620\u5c04\u7ed3\u679c
         */
        @Select("<script>SELECT id" + FILTER + " ORDER BY id <if test='limit &gt;= 0'>LIMIT #{limit} OFFSET #{offset}</if></script>")
        List<Entry> selectQuery(@Param("min") Integer min, @Param("offset") long offset, @Param("limit") long limit);
        /**
         * \u7edf\u8ba1\u5f53\u524d\u6d4b\u8bd5\u8868\u4e2d\u7b26\u5408\u6761\u4ef6\u7684\u8bb0\u5f55\u6570\u3002
         * @param min \u67e5\u8be2\u5141\u8bb8\u8fd4\u56de\u7684\u6700\u5c0f\u4e1a\u52a1\u503c
         * @return \u6ee1\u8db3\u6620\u5c04\u67e5\u8be2\u6761\u4ef6\u7684\u884c\u6570
         */
        @Select("<script>SELECT COUNT(*)" + FILTER + "</script>")
        long countQuery(@Param("min") Integer min);
        /**
         * \u4f9d\u636e\u4e3b\u952e\u6761\u4ef6\u79fb\u9664\u6d4b\u8bd5\u8868\u4e2d\u5339\u914d\u7684\u8bb0\u5f55\u3002
         * @param id \u88ab\u67e5\u627e\u3001\u5199\u5165\u6216\u5220\u9664\u7684\u5939\u5177\u8bb0\u5f55\u4e3b\u952e
         * @return \u5220\u9664\u64cd\u4f5c\u5f71\u54cd\u7684\u884c\u6570
         */
        @Delete("DELETE FROM native_entry WHERE id = #{id}")
        int deleteById(Integer id);
        /**
         * \u4f9d\u636e\u4e3b\u952e\u6761\u4ef6\u79fb\u9664\u6d4b\u8bd5\u8868\u4e2d\u5339\u914d\u7684\u8bb0\u5f55\u3002
         * @param ids \u5f53\u524d\u6279\u91cf\u5220\u9664\u7684\u6d4b\u8bd5\u4e3b\u952e\u96c6\u5408
         * @return \u5220\u9664\u64cd\u4f5c\u5f71\u54cd\u7684\u884c\u6570
         */
        @Delete("<script>DELETE FROM native_entry WHERE id IN <foreach collection='ids' item='id' open='(' close=')' separator=','>#{id}</foreach></script>")
        int deleteByIds(@Param("ids") Collection<Integer> ids);
    }

    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u6301\u4e45\u5316\u7684\u805a\u5408\u6837\u672c\u3002 */
    public static class Entry extends AggregateRoot<Integer> {
        /** \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
        private Integer id;
        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        @Override public Integer id() { return id; }
        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        public Integer getId() { return id; }
        /**
         * \u8bbe\u7f6e<code>id</code> \u5b57\u6bb5\u3002
         * @param id \u8981\u5199\u5165\u7684\u503c
         */
        public void setId(Integer id) { this.id = id; }
    }

    /** \u5b9e\u73b0 <code>MybatisAggregateRepository</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>selectRows()</code>\u3001<code>countRows()</code>\u3001<code>deleteRow()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    static class TestRepository extends MybatisAggregateRepository<EntryMapper, Entry, Entry, Query<Entry>, Integer> {
        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestRepository</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param mapper \u5f53\u524d\u6d4b\u8bd5\u767b\u8bb0\u6216\u4f7f\u7528\u7684\u6301\u4e45\u5316\u6620\u5c04\u7ec4\u4ef6
         */
        TestRepository(EntryMapper mapper) { super(mapper, Entry.class, Entry.class); }

        /**
         * 查询符合条件的持久化记录。
         *
         * <p>测试夹具会调用 <code>baseMapper.selectQuery(minimumId(query), offset, limit)</code>、<code>minimumId(query)</code>。</p>
         *
         * @param query 领域查询，null 表示无额外条件
         * @param offset 从零开始的偏移量
         * @param limit 最大行数，负数表示不分页
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
         * \u901a\u8fc7 <code>baseMapper.deleteById()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>baseMapper.deleteById(id)</code>
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
         * \u6309 <code>(Objects.isNull(query))</code> \u7b49\u5b9e\u9645\u6761\u4ef6\u9009\u62e9\u5f53\u524d\u7528\u4f8b\u8fd4\u56de\u7684\u7ed3\u679c\u3002
         * @param query \u7528\u6765\u8bfb\u53d6\u6d4b\u8bd5\u6570\u636e\u7684\u67e5\u8be2\u5bf9\u8c61
         * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>null</code>\u3001<code>minimum</code>
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
