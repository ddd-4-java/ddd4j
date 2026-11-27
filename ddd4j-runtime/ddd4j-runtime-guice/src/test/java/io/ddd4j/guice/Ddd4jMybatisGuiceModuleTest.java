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

/** \u901a\u8fc7 <code>source.setURL()</code>\u3001<code>Guice.createInjector()</code>\u3001<code>module.initRepositories()</code> \u9a8c\u8bc1 <code>Ddd4jMybatisGuiceModule</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class Ddd4jMybatisGuiceModuleTest {
    /** \u9a8c\u8bc1<code>injector.getInstance(InjectedRepository.class).getBaseMapper().ping()</code> \u7b49\u4e8e <code>1</code>\u3002 */
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

    /** \u7528\u4e8e\u66ff\u4ee3\u6301\u4e45\u5316 Mapper \u4ee5\u9a8c\u8bc1\u4ed3\u50a8\u53c2\u6570\u6216\u56de\u586b\u7ed3\u679c\u3002 */
    public interface RepositoryMapper extends BaseMapper<RepositoryPo> {
        /**
         * \u6267\u884c\u4e00\u6b21\u65e0\u5916\u90e8\u6570\u636e\u4f9d\u8d56\u7684 SQL \u7528\u4e8e\u9a8c\u8bc1 Mapper \u6ce8\u518c\u3002
         * @return \u67e5\u8be2\u5e38\u91cf 1
         */
        @Select("SELECT 1")
        int ping();
    }

    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u6301\u4e45\u5316\u7684\u805a\u5408\u6837\u672c\u3002 */
    public static class RepositoryModel extends AggregateRoot<Long> {
        /**
         * \u4e3a\u5f53\u524d\u7528\u4f8b\u63d0\u4f9b\u56fa\u5b9a\u7684 <code>id</code> \u8fd4\u56de\u503c\u3002
         * @return <code>1L</code>
         */
        @Override
        public Long id() { return 1L; }
    }

    /** \u7528\u4e8e\u63a7\u5236\u67e5\u8be2\u6761\u4ef6\u6216\u8def\u7531\u7c7b\u578b\u7684\u6d4b\u8bd5\u67e5\u8be2\u3002 */
    public static class RepositoryQuery extends Query<RepositoryModel> { }

    /** \u5c06 <code>id</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    @Getter
    @Setter
    public static class RepositoryPo {
        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        @TableId
        private Long id;
    }

    /** \u4f9b\u5bb9\u5668\u6ce8\u5165\u6d4b\u8bd5\u89e3\u6790\u7684\u793a\u4f8b\u4ed3\u50a8\u3002 */
    public static class InjectedRepository extends BaseRepositoryImpl<
            RepositoryMapper, RepositoryModel, RepositoryPo, RepositoryQuery, Long> {
        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>InjectedRepository</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        public InjectedRepository() { super(); }
    }


    /** \u9a8c\u8bc1<code>factory.getConfiguration().getInterceptors()</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\uff0c<code>factory.getConfiguration().getInterceptors().get(0)</code> \u7684\u7c7b\u578b\u4e3a <code>MybatisPlusEnhanceInterceptor.class</code>\uff0c<code>query.filled.get(0)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>results.get(0)</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>session</code> \u7684\u7c7b\u578b\u4e3a <code>org.apache.ibatis.session.SqlSessionManager.class</code>\uff0c<code>mapper.count()</code>\u4e3a\u96f6\uff0c<code>mapper.count()</code> \u7b49\u4e8e <code>1</code>\u3002 */
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
     * \u9a8c\u8bc1<code>session.getMapper(ScopedMapper.class).find(1)</code> \u7b49\u4e8e <code>"a"</code>\uff0c<code>session.getMapper(ScopedMapper.class).find(2)</code>\u4e3a\u7a7a\uff0c<code>(Object) io.ddd4j.core.context.ThreadContext.get(io.ddd4j.core.constant.ContextConstants.PREPARING_SQL)</code>\u975e\u7a7a\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u3001\u6267\u884c\u6570\u636e\u5e93\u64cd\u4f5c\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>mapper.selectById(1).getSecret()</code> \u7b49\u4e8e <code>"13800138000"</code>\uff0c<code>rows.next()</code>\u4e3a\u771f\uff0c<code>session.getMapper(CryptoMapper.class).selectById(1)</code> \u629b\u51fa <code>org.apache.ibatis.exceptions.PersistenceException.class</code>\u3002
     * @throws Exception \u6267\u884c\u6570\u636e\u5e93\u64cd\u4f5c\u65f6\u5931\u8d25
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

    /** \u7528\u4e8e\u66ff\u4ee3\u6301\u4e45\u5316 Mapper \u4ee5\u9a8c\u8bc1\u4ed3\u50a8\u53c2\u6570\u6216\u56de\u586b\u7ed3\u679c\u3002 */
    public interface CryptoMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<CryptoEntry> { }

    /** \u5c06 <code>id</code>\u3001<code>secret</code>\u3001<code>signature</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    @lombok.Data
    @com.baomidou.mybatisplus.annotation.TableName("crypto_entry")
    @org.apache.ibatis.enhance.annotation.crypto.EncryptedTable
    @org.apache.ibatis.enhance.annotation.crypto.TableSignature
    public static class CryptoEntry {
        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        @com.baomidou.mybatisplus.annotation.TableId
        private Integer id;
        /** \u5f53\u524d\u7b7e\u540d\u3001\u52a0\u5bc6\u6216\u4ee4\u724c\u6821\u9a8c\u6d4b\u8bd5\u4f7f\u7528\u7684\u5bc6\u94a5\u6837\u672c\u3002 */
        @org.apache.ibatis.enhance.annotation.crypto.EncryptedField
        @org.apache.ibatis.enhance.annotation.crypto.TableSignatureField
        private String secret;
        /** \u9a8c\u7b7e\u3001\u52a0\u5bc6\u5b57\u6bb5\u6216\u6301\u4e45\u5316\u5f80\u8fd4\u6d4b\u8bd5\u4f7f\u7528\u7684\u7b7e\u540d\u503c\u3002 */
        @org.apache.ibatis.enhance.annotation.crypto.TableSignatureField(stored = true)
        private String signature;
    }

    /** \u63d0\u4f9b <code>find()</code>\u3001<code>remove()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    public interface ScopedMapper {
        /**
         * \u6309\u6620\u5c04 SQL \u8bfb\u53d6\u6d4b\u8bd5\u8bb0\u5f55\u5e76\u8fd4\u56de\u79df\u6237\u5217\u3002
         * @param id \u88ab\u67e5\u627e\u3001\u5199\u5165\u6216\u5220\u9664\u7684\u5939\u5177\u8bb0\u5f55\u4e3b\u952e
         * @return \u4e0e\u67e5\u8be2\u5339\u914d\u7684\u6620\u5c04\u7ed3\u679c
         */
        @Select("SELECT tenant_id FROM tenant_entry WHERE id = #{id}")
        String find(int id);
        /**
         * \u4f9d\u636e\u4e3b\u952e\u6761\u4ef6\u79fb\u9664\u6d4b\u8bd5\u8868\u4e2d\u5339\u914d\u7684\u8bb0\u5f55\u3002
         * @param id \u88ab\u67e5\u627e\u3001\u5199\u5165\u6216\u5220\u9664\u7684\u5939\u5177\u8bb0\u5f55\u4e3b\u952e
         * @return \u5220\u9664\u64cd\u4f5c\u5f71\u54cd\u7684\u884c\u6570
         */
        @org.apache.ibatis.annotations.Delete("DELETE FROM tenant_entry WHERE id = #{id}")
        int remove(int id);
    }

    /** \u63d0\u4f9b <code>create()</code>\u3001<code>insert()</code>\u3001<code>count()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    public interface WriteMapper {
        /** \u5728\u6d4b\u8bd5\u5f00\u59cb\u524d\u786e\u4fdd\u4f1a\u8bdd\u6d4b\u8bd5\u8868\u5b58\u5728\u3002 */
        @org.apache.ibatis.annotations.Update("CREATE TABLE IF NOT EXISTS session_entry(id INT PRIMARY KEY)")
        void create();
        /**
         * \u628a\u7ed9\u5b9a\u5939\u5177\u6570\u636e\u63d2\u5165\u5f53\u524d\u6d4b\u8bd5\u8868\u3002
         * @param id \u88ab\u67e5\u627e\u3001\u5199\u5165\u6216\u5220\u9664\u7684\u5939\u5177\u8bb0\u5f55\u4e3b\u952e
         */
        @org.apache.ibatis.annotations.Insert("INSERT INTO session_entry VALUES(#{id})")
        void insert(int id);
        /**
         * \u7edf\u8ba1\u5f53\u524d\u6d4b\u8bd5\u8868\u4e2d\u7b26\u5408\u6761\u4ef6\u7684\u8bb0\u5f55\u6570\u3002
         * @return \u6ee1\u8db3\u6620\u5c04\u67e5\u8be2\u6761\u4ef6\u7684\u884c\u6570
         */
        @Select("SELECT COUNT(*) FROM session_entry")
        int count();
    }

    /** \u63d0\u4f9b <code>find()</code> \u7b49\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u72ec\u7acb\u64cd\u4f5c\u3002 */
    public interface AggregateMapper {

        /**
         * \u6309\u6620\u5c04 SQL \u8bfb\u53d6\u6d4b\u8bd5\u8bb0\u5f55\u3002
         * @param query \u968f\u6620\u5c04\u67e5\u8be2\u4f20\u5165\u7684\u6d4b\u8bd5\u9886\u57df\u6761\u4ef6
         * @return \u4e0e\u67e5\u8be2\u5339\u914d\u7684\u6620\u5c04\u7ed3\u679c
         */
        @Select("SELECT 'aggregate-1' AS id")
        List<TestAggregate> find(@Param("query") TrackingQuery query);
    }

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\uff0c\u4f9b\u9a8c\u8bc1\u805a\u5408\u6807\u8bc6\u548c\u4ed3\u50a8\u6216\u4e8b\u4ef6\u5904\u7406\u884c\u4e3a\u3002 */
    public static final class TestAggregate extends AggregateRoot<String> {

        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private String id;

        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        @Override
        public String id() {
            return id;
        }

        /**
         * \u8bbe\u7f6e<code>id</code> \u5b57\u6bb5\u3002
         * @param id \u8981\u5199\u5165\u7684\u503c
         */
        public void setId(String id) {
            this.id = id;
        }
    }

    /** \u8bb0\u5f55\u6216\u66b4\u9732\u7b5b\u9009\u6761\u4ef6\u7684\u67e5\u8be2\u66ff\u8eab\u3002 */
    public static final class TrackingQuery extends Query<TestAggregate> {

        /** \u8bb0\u5f55 ORM \u7ed3\u679c\u586b\u5145\u903b\u8f91\u5b9e\u9645\u6267\u884c\u7684\u6b21\u6570\u3002 */
        private int fillCount;
        /** \u4fdd\u5b58\u5df2\u4ea4\u7ed9 ORM \u7ed3\u679c\u586b\u5145\u5165\u53e3\u7684\u6570\u636e\u3002 */
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
