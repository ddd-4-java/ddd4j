/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.guice;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.enhance.crypto.key.CryptoKeyMaterial;
import com.baomidou.mybatisplus.enhance.crypto.key.StaticCryptoKeyProvider;
import com.baomidou.mybatisplus.enhance.plugins.MybatisPlusEnhanceInterceptor;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.data.mybatis.repository.impl.BaseRepositoryImpl;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过 <code>source.setURL()</code>、<code>Guice.createInjector()</code>、<code>module.initRepositories()</code> 验证 <code>Ddd4jMybatisGuiceModule</code> 的运行行为。
 */
class Ddd4jMybatisGuiceModuleTest {
    /**
     * 验证<code>injector.getInstance(InjectedRepository.class).getBaseMapper().ping()</code> 等于 <code>1</code>。
     */
    @Test
    void injectsMapperIntoTheSingleAggregateRepository() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:repository_injection");
        Ddd4jMybatisGuiceModule module = new Ddd4jMybatisGuiceModule(source)
                .addMapper(RepositoryMapper.class)
                .bindRepository(InjectedRepository.class, RepositoryMapper.class);
        Injector injector = Guice.createInjector(module);
        module.initRepositories(injector);
        assertThat(injector.getInstance(InjectedRepository.class).getBaseMapper().ping()).isEqualTo(1);
    }

    /**
     * 验证<code>factory.getConfiguration().getInterceptors()</code> 的大小为 <code>1</code>，<code>factory.getConfiguration().getInterceptors().get(0)</code> 的类型为 <code>MybatisPlusEnhanceInterceptor.class</code>，<code>query.filled.get(0)</code> 指向同一实例 <code>results.get(0)</code>。
     */
    @Test
    void fillsMapperAggregateThroughTheEnhanceQueryChain() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:");
        SqlSessionFactory factory = new Ddd4jMybatisGuiceModule(dataSource)
                .addMapper(AggregateMapper.class)
                .sqlSessionFactory();
        assertThat(factory.getConfiguration().getInterceptors()).hasSize(1);
        assertThat(factory.getConfiguration().getInterceptors().get(0))
                .isInstanceOf(MybatisPlusEnhanceInterceptor.class);

        try (SqlSession session = factory.openSession()) {
            TrackingQuery query = new TrackingQuery();
            List<TestAggregate> results = session.getMapper(AggregateMapper.class).find(query);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).id()).isEqualTo("aggregate-1");
            assertThat(query.fillCount).isEqualTo(1);
            assertThat(query.filled).hasSize(1);
            assertThat(query.filled.get(0)).isSameAs(results.get(0));
        }
    }

    /**
     * 验证<code>session</code> 的类型为 <code>org.apache.ibatis.session.SqlSessionManager.class</code>，<code>mapper.count()</code>为零，<code>mapper.count()</code> 等于 <code>1</code>。
     */
    @Test
    void injectedSessionMustManageAndRollbackTransaction() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:managed;DB_CLOSE_DELAY=-1");
        Ddd4jMybatisGuiceModule module = new Ddd4jMybatisGuiceModule(dataSource).addMapper(WriteMapper.class);
        SqlSessionFactory factory = module.sqlSessionFactory();
        SqlSession session = module.sqlSession(factory);
        assertThat(session).isInstanceOf(org.apache.ibatis.session.SqlSessionManager.class);
        org.apache.ibatis.session.SqlSessionManager manager = (org.apache.ibatis.session.SqlSessionManager) session;
        WriteMapper mapper = manager.getMapper(WriteMapper.class);
        mapper.create();
        manager.startManagedSession(false);
        try {
            mapper.insert(1);
            manager.rollback();
        } finally {
            manager.close();
        }
        assertThat(mapper.count()).isZero();
        manager.startManagedSession(false);
        try {
            mapper.insert(2);
            manager.commit();
        } finally {
            manager.close();
        }
        assertThat(mapper.count()).isEqualTo(1);
    }

    /**
     * 验证<code>session.getMapper(ScopedMapper.class).find(1)</code> 等于 <code>"a"</code>，<code>session.getMapper(ScopedMapper.class).find(2)</code>为空，<code>(Object) io.ddd4j.core.context.ThreadContext.get(io.ddd4j.core.constant.ContextConstants.PREPARING_SQL)</code>非空。
     *
     * @throws Exception 反射访问被测成员、执行数据库操作时失败
     */
    @Test
    void unifiedConfigurerMustScopeMapperByIdAndPublishExecution() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:scoped;DB_CLOSE_DELAY=-1");
        try (java.sql.Connection connection = dataSource.getConnection(); java.sql.Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tenant_entry(id INT PRIMARY KEY, tenant_id VARCHAR(40))");
            statement.execute("INSERT INTO tenant_entry VALUES(1, 'a'), (2, 'b')");
        }
        Class<?> type = Class.forName("io.ddd4j.data.mybatis.config.Ddd4jMybatisConfigurer");
        Object configurer = type.getConstructor().newInstance();
        type.getMethod("tenant", String.class, java.util.function.Predicate.class)
                .invoke(configurer, "tenant_id", (java.util.function.Predicate<String>) table -> false);
        MybatisPlusEnhanceInterceptor chain = (MybatisPlusEnhanceInterceptor) type.getMethod("build").invoke(configurer);
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.addInterceptor(chain);
        configuration.setEnvironment(new org.apache.ibatis.mapping.Environment("scope",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), dataSource));
        configuration.addMapper(ScopedMapper.class);
        SqlSessionFactory factory = new com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder().build(configuration);
        io.ddd4j.core.context.ThreadContext.set(io.ddd4j.core.constant.ContextConstants.TENANT_ID, "a");
        try (SqlSession session = factory.openSession()) {
            assertThat(session.getMapper(ScopedMapper.class).find(1)).isEqualTo("a");
            assertThat(session.getMapper(ScopedMapper.class).find(2)).isNull();
            assertThat(session.getMapper(ScopedMapper.class).remove(2)).isZero();
            assertThat((Object) io.ddd4j.core.context.ThreadContext.get(io.ddd4j.core.constant.ContextConstants.PREPARING_SQL)).isNotNull();
        } finally {
            io.ddd4j.core.context.ThreadContext.clear();
        }
    }

    /**
     * 验证<code>mapper.selectById(1).getSecret()</code> 等于 <code>"13800138000"</code>，<code>rows.next()</code>为真，<code>session.getMapper(CryptoMapper.class).selectById(1)</code> 抛出 <code>org.apache.ibatis.exceptions.PersistenceException.class</code>。
     *
     * @throws Exception 执行数据库操作时失败
     */
    @Test
    void configuredComponentMustEncryptSignAndKeepCachedCiphertext() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:crypto_chain;DB_CLOSE_DELAY=-1");
        try (java.sql.Connection connection = source.getConnection(); java.sql.Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE crypto_entry(id INT PRIMARY KEY, secret VARCHAR(2048), signature VARCHAR(2048))");
        }
        CryptoKeyMaterial key = new CryptoKeyMaterial(
                "guice-test",
                "1234567890abcdef".getBytes(StandardCharsets.UTF_8),
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        com.baomidou.mybatisplus.enhance.crypto.handler.DefaultEncryptedFieldHandler handler =
                new com.baomidou.mybatisplus.enhance.crypto.handler.DefaultEncryptedFieldHandler(
                        new com.fasterxml.jackson.databind.ObjectMapper(),
                        com.baomidou.mybatisplus.enhance.crypto.enums.SymmetricAlgorithmType.AES,
                        cn.hutool.crypto.digest.HmacAlgorithm.HmacSHA256,
                        cn.hutool.crypto.Mode.CBC, cn.hutool.crypto.Padding.PKCS5Padding,
                        new StaticCryptoKeyProvider(key));
        SqlSessionFactory factory = new Ddd4jMybatisGuiceModule(source).addMapper(CryptoMapper.class)
                .enhancements(new io.ddd4j.data.mybatis.config.Ddd4jMybatisConfigurer().encryption(handler).signature(true))
                .sqlSessionFactory();
        try (SqlSession session = factory.openSession(true)) {
            CryptoMapper mapper = session.getMapper(CryptoMapper.class);
            CryptoEntry entry = new CryptoEntry();
            entry.setId(1);
            entry.setSecret("13800138000");
            mapper.insert(entry);
            assertThat(mapper.selectById(1).getSecret()).isEqualTo("13800138000");
            assertThat(mapper.selectById(1).getSecret()).isEqualTo("13800138000");
        }
        try (java.sql.Connection connection = source.getConnection(); java.sql.Statement statement = connection.createStatement()) {
            try (java.sql.ResultSet rows = statement.executeQuery("SELECT secret FROM crypto_entry WHERE id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isNotEqualTo("13800138000");
            }
            statement.executeUpdate("UPDATE crypto_entry SET signature = 'tampered' WHERE id = 1");
        }
        try (SqlSession session = factory.openSession()) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> session.getMapper(CryptoMapper.class).selectById(1))
                    .isInstanceOf(org.apache.ibatis.exceptions.PersistenceException.class);
        }
    }

    /**
     * 用于替代持久化 Mapper 以验证仓储参数或回填结果。
     */
    public interface RepositoryMapper extends BaseMapper<RepositoryPo> {
        /**
         * 执行一次无外部数据依赖的 SQL 用于验证 Mapper 注册。
         *
         * @return 查询常量 1
         */
        @Select("SELECT 1")
        int ping();
    }


    /**
     * 用于替代持久化 Mapper 以验证仓储参数或回填结果。
     */
    public interface CryptoMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<CryptoEntry> {
    }

    /**
     * 提供 <code>find()</code>、<code>remove()</code> 等供当前测试使用的独立操作。
     */
    public interface ScopedMapper {
        /**
         * 按映射 SQL 读取测试记录并返回租户列。
         *
         * @param id 被查找、写入或删除的夹具记录主键
         * @return 与查询匹配的映射结果
         */
        @Select("SELECT tenant_id FROM tenant_entry WHERE id = #{id}")
        String find(int id);

        /**
         * 依据主键条件移除测试表中匹配的记录。
         *
         * @param id 被查找、写入或删除的夹具记录主键
         * @return 删除操作影响的行数
         */
        @org.apache.ibatis.annotations.Delete("DELETE FROM tenant_entry WHERE id = #{id}")
        int remove(int id);
    }

    /**
     * 提供 <code>create()</code>、<code>insert()</code>、<code>count()</code> 等供当前测试使用的独立操作。
     */
    public interface WriteMapper {
        /**
         * 在测试开始前确保会话测试表存在。
         */
        @org.apache.ibatis.annotations.Update("CREATE TABLE IF NOT EXISTS session_entry(id INT PRIMARY KEY)")
        void create();

        /**
         * 把给定夹具数据插入当前测试表。
         *
         * @param id 被查找、写入或删除的夹具记录主键
         */
        @org.apache.ibatis.annotations.Insert("INSERT INTO session_entry VALUES(#{id})")
        void insert(int id);

        /**
         * 统计当前测试表中符合条件的记录数。
         *
         * @return 满足映射查询条件的行数
         */
        @Select("SELECT COUNT(*) FROM session_entry")
        int count();
    }

    /**
     * 提供 <code>find()</code> 等供当前测试使用的独立操作。
     */
    public interface AggregateMapper {

        /**
         * 按映射 SQL 读取测试记录。
         *
         * @param query 随映射查询传入的测试领域条件
         * @return 与查询匹配的映射结果
         */
        @Select("SELECT 'aggregate-1' AS id")
        List<TestAggregate> find(@Param("query") TrackingQuery query);
    }

    /**
     * 供测试验证事件应用和持久化的聚合样本。
     */
    public static class RepositoryModel extends AggregateRoot<Long> {
        /**
         * 为当前用例提供固定的 <code>id</code> 返回值。
         *
         * @return <code>1L</code>
         */
        @Override
        public Long id() {
            return 1L;
        }
    }

    /**
     * 用于控制查询条件或路由类型的测试查询。
     */
    public static class RepositoryQuery extends Query<RepositoryModel> {
    }

    /**
     * 将 <code>id</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    @Getter
    @Setter
    public static class RepositoryPo {
        /**
         * 标识当前测试对象的主键或事件标识。
         */
        @TableId
        private Long id;
    }

    /**
     * 供容器注入测试解析的示例仓储。
     */
    public static class InjectedRepository extends BaseRepositoryImpl<
            RepositoryMapper, RepositoryModel, RepositoryPo, RepositoryQuery, Long> {
        /**
         * 使用 <code>super()</code> 初始化 <code>InjectedRepository</code> 测试夹具。
         */
        public InjectedRepository() {
            super();
        }
    }

    /**
     * 将 <code>id</code>、<code>secret</code>、<code>signature</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    @lombok.Data
    @com.baomidou.mybatisplus.annotation.TableName("crypto_entry")
    @org.apache.ibatis.enhance.annotation.crypto.EncryptedTable
    @org.apache.ibatis.enhance.annotation.crypto.TableSignature
    public static class CryptoEntry {
        /**
         * 标识当前测试对象的主键或事件标识。
         */
        @com.baomidou.mybatisplus.annotation.TableId
        private Integer id;
        /**
         * 当前签名、加密或令牌校验测试使用的密钥样本。
         */
        @org.apache.ibatis.enhance.annotation.crypto.EncryptedField
        @org.apache.ibatis.enhance.annotation.crypto.TableSignatureField
        private String secret;
        /**
         * 验签、加密字段或持久化往返测试使用的签名值。
         */
        @org.apache.ibatis.enhance.annotation.crypto.TableSignatureField(stored = true)
        private String signature;
    }

    /**
     * 测试专用聚合，供验证聚合标识和仓储或事件处理行为。
     */
    public static final class TestAggregate extends AggregateRoot<String> {

        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private String id;

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        @Override
        public String id() {
            return id;
        }

        /**
         * 设置<code>id</code> 字段。
         *
         * @param id 要写入的值
         */
        public void setId(String id) {
            this.id = id;
        }
    }

    /**
     * 记录或暴露筛选条件的查询替身。
     */
    public static final class TrackingQuery extends Query<TestAggregate> {

        /**
         * 记录 ORM 结果填充逻辑实际执行的次数。
         */
        private int fillCount;
        /**
         * 保存已交给 ORM 结果填充入口的数据。
         */
        private List<? extends AggregateRoot<?>> filled;

        /**
         * 补充当前查询执行所需的参数。
         *
         * <p>此实现更新<code>filled = models</code>。</p>
         *
         * @param models 待填充的聚合根列表
         */
        @Override
        public void doFills(List<? extends AggregateRoot<?>> models) {
            fillCount++;
            filled = models;
        }
    }
}
