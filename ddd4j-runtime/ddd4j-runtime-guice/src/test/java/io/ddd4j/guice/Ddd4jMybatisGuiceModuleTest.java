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

class Ddd4jMybatisGuiceModuleTest {
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

    public interface RepositoryMapper extends BaseMapper<RepositoryPo> {
        @Select("SELECT 1")
        int ping();
    }

    public static class RepositoryModel extends AggregateRoot<Long> {
        @Override
        public Long id() { return 1L; }
    }

    public static class RepositoryQuery extends Query<RepositoryModel> { }

    @Getter
    @Setter
    public static class RepositoryPo {
        @TableId
        private Long id;
    }

    public static class InjectedRepository extends BaseRepositoryImpl<
            RepositoryMapper, RepositoryModel, RepositoryPo, RepositoryQuery, Long> {
        public InjectedRepository() { super(); }
    }


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

    public interface CryptoMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<CryptoEntry> { }

    @lombok.Data
    @com.baomidou.mybatisplus.annotation.TableName("crypto_entry")
    @org.apache.ibatis.enhance.annotation.crypto.EncryptedTable
    @org.apache.ibatis.enhance.annotation.crypto.TableSignature
    public static class CryptoEntry {
        @com.baomidou.mybatisplus.annotation.TableId
        private Integer id;
        @org.apache.ibatis.enhance.annotation.crypto.EncryptedField
        @org.apache.ibatis.enhance.annotation.crypto.TableSignatureField
        private String secret;
        @org.apache.ibatis.enhance.annotation.crypto.TableSignatureField(stored = true)
        private String signature;
    }

    public interface ScopedMapper {
        @Select("SELECT tenant_id FROM tenant_entry WHERE id = #{id}")
        String find(int id);
        @org.apache.ibatis.annotations.Delete("DELETE FROM tenant_entry WHERE id = #{id}")
        int remove(int id);
    }

    public interface WriteMapper {
        @org.apache.ibatis.annotations.Update("CREATE TABLE IF NOT EXISTS session_entry(id INT PRIMARY KEY)")
        void create();
        @org.apache.ibatis.annotations.Insert("INSERT INTO session_entry VALUES(#{id})")
        void insert(int id);
        @Select("SELECT COUNT(*) FROM session_entry")
        int count();
    }

    public interface AggregateMapper {

        @Select("SELECT 'aggregate-1' AS id")
        List<TestAggregate> find(@Param("query") TrackingQuery query);
    }

    public static final class TestAggregate extends AggregateRoot<String> {

        private String id;

        @Override
        public String id() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    public static final class TrackingQuery extends Query<TestAggregate> {

        private int fillCount;
        private List<? extends AggregateRoot<?>> filled;

        @Override
        public void doFills(List<? extends AggregateRoot<?>> models) {
            fillCount++;
            filled = models;
        }
    }
}
