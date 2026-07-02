package io.ddd4j.guice;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.ddd4j.core.domain.event.TypeHandlerRegistry;
import io.ddd4j.data.mybatis.config.BaseDataProperties;
import io.ddd4j.data.mybatis.repository.impl.BaseRepositoryImpl;
import io.ddd4j.data.mybatis.typehandler.MybatisTypeHandlerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * ddd4j MyBatis 的 Guice 桥接模块。
 */
@Slf4j
public class Ddd4jMybatisGuiceModule extends AbstractModule {

    private final DataSource dataSource;
    private final Set<Class<?>> mapperInterfaces = new LinkedHashSet<>();
    private final Map<Class<?>, Class<?>> repositoryToMapper = new LinkedHashMap<>();

    public Ddd4jMybatisGuiceModule(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Ddd4jMybatisGuiceModule addMapper(Class<?> mapperInterface) {
        this.mapperInterfaces.add(mapperInterface);
        return this;
    }

    public Ddd4jMybatisGuiceModule bindRepository(Class<?> repositoryImpl, Class<?> mapperInterface) {
        this.repositoryToMapper.put(repositoryImpl, mapperInterface);
        this.mapperInterfaces.add(mapperInterface);
        return this;
    }

    @Override
    protected void configure() {
        bind(BaseDataProperties.class).in(Singleton.class);
        bind(TypeHandlerRegistry.class).to(MybatisTypeHandlerRegistry.class).in(Singleton.class);
        for (Class<?> repositoryImpl : repositoryToMapper.keySet()) {
            bind(repositoryImpl).in(Singleton.class);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void initRepositories(Injector injector) {
        SqlSession sqlSession = injector.getInstance(SqlSession.class);
        for (Map.Entry<Class<?>, Class<?>> entry : repositoryToMapper.entrySet()) {
            Class<?> repositoryImpl = entry.getKey();
            Class<?> mapperInterface = entry.getValue();
            try {
                BaseRepositoryImpl repository = (BaseRepositoryImpl) injector.getInstance(repositoryImpl);
                Object mapper = sqlSession.getMapper(mapperInterface);
                Method setMapper = BaseRepositoryImpl.class.getMethod("setMapper", BaseMapper.class);
                setMapper.invoke(repository, mapper);
                log.debug("Injected mapper {} into repository {}",
                        mapperInterface.getSimpleName(), repositoryImpl.getSimpleName());
            } catch (Exception exception) {
                log.warn("Failed to inject mapper into {}: {}", repositoryImpl.getSimpleName(),
                        exception.getMessage());
            }
        }
        log.info("Repository mapper injection completed: {} repositories", repositoryToMapper.size());
    }

    @Provides
    @Singleton
    public SqlSessionFactory sqlSessionFactory() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        for (Class<?> mapperInterface : mapperInterfaces) {
            if (!configuration.hasMapper(mapperInterface)) {
                configuration.addMapper(mapperInterface);
                log.debug("Registered MyBatis mapper: {}", mapperInterface.getSimpleName());
            }
        }

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        configuration.addInterceptor(interceptor);
        configuration.setEnvironment(new Environment("ddd4j-runtime-guice", new JdbcTransactionFactory(), dataSource));

        SqlSessionFactory factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        log.info("SqlSessionFactory created: {} mappers registered", mapperInterfaces.size());
        return factory;
    }

    @Provides
    @Singleton
    public SqlSession sqlSession(SqlSessionFactory sqlSessionFactory) {
        return sqlSessionFactory.openSession(true);
    }
}
