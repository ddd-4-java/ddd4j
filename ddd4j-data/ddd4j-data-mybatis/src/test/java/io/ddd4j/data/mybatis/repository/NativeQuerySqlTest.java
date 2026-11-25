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

class NativeQuerySqlTest {
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

    interface TenantMapper {
        @Select("SELECT tenant_id FROM native_tenant WHERE id = #{id}")
        String find(int id);
        @Delete("DELETE FROM native_tenant WHERE id = #{id}")
        int remove(int id);
        @org.apache.ibatis.annotations.Insert("INSERT INTO native_tenant(id, tenant_id) VALUES(#{id}, #{tenant})")
        int insert(@Param("id") int id, @Param("tenant") String tenant);
    }

    interface EntryMapper extends EnhanceMapper<Entry> {
        @Override
        default List<Entry> selectList() { throw new AssertionError("query must not load all rows"); }
        String FILTER = " FROM native_entry <if test='min != null'>WHERE id &gt; #{min}</if>";
        @Select("<script>SELECT id" + FILTER + " ORDER BY id <if test='limit &gt;= 0'>LIMIT #{limit} OFFSET #{offset}</if></script>")
        List<Entry> selectQuery(@Param("min") Integer min, @Param("offset") long offset, @Param("limit") long limit);
        @Select("<script>SELECT COUNT(*)" + FILTER + "</script>")
        long countQuery(@Param("min") Integer min);
        @Delete("DELETE FROM native_entry WHERE id = #{id}")
        int deleteById(Integer id);
        @Delete("<script>DELETE FROM native_entry WHERE id IN <foreach collection='ids' item='id' open='(' close=')' separator=','>#{id}</foreach></script>")
        int deleteByIds(@Param("ids") Collection<Integer> ids);
    }

    public static class Entry extends AggregateRoot<Integer> {
        private Integer id;
        @Override public Integer id() { return id; }
        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
    }

    static class TestRepository extends MybatisAggregateRepository<EntryMapper, Entry, Entry, Query<Entry>, Integer> {
        TestRepository(EntryMapper mapper) { super(mapper, Entry.class, Entry.class); }

        @Override
        protected List<Entry> selectRows(Query<Entry> query, long offset, long limit) {
            return baseMapper.selectQuery(minimumId(query), offset, limit);
        }

        @Override
        protected long countRows(Query<Entry> query) {
            return baseMapper.countQuery(minimumId(query));
        }

        @Override
        protected int deleteRow(Integer id) {
            return baseMapper.deleteById(id);
        }

        @Override
        protected int deleteRows(Collection<Integer> ids) {
            return baseMapper.deleteByIds(ids);
        }

        // 本示例的 SQL 仅接受可选的 id > min；在查询前拒绝其他条件。
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
