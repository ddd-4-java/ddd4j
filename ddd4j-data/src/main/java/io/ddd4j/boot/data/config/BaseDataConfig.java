package io.ddd4j.boot.data.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

import javax.sql.DataSource;

/**
 * MyBatis Plus 官方插件配置。
 *
 * <p>使用 MP 官方的 {@link MybatisPlusInterceptor} 替代自定义插件，避免与 ddd4j-cloud 体系冲突。
 * 装配的插件：
 * <ul>
 *   <li>{@link PaginationInnerInterceptor} — 分页（自动注入 LIMIT + COUNT）</li>
 *   <li>{@link OptimisticLockerInnerInterceptor} — 乐观锁（@Version 自动处理）</li>
 *   <li>{@link BlockAttackInnerInterceptor} — 防全表攻击（阻止无 WHERE 的 UPDATE/DELETE）</li>
 * </ul>
 *
 * <p>业务项目可通过 {@code // @ConditionalOnMissingBean} 覆盖此配置，
 * 自行注册 {@link MybatisPlusInterceptor}（如需要 TenantLineInnerInterceptor 等额外插件）。
 *
 * @author wandl
 * @since 3.4.x
 */
@Configuration(proxyBeanMethods = false)
// @EnableConfigurationProperties(BaseDataProperties.class)
// @ConditionalOnBean(DataSource.class)
// @AutoConfigureAfter({DataSourceAutoConfiguration.class})
public class BaseDataConfig {

    /**
     * MyBatis Plus 官方拦截器，聚合多个 InnerInterceptor。
     *
     * <p>参考 ddd4j-cloud 的 MybatisPlusConfig 和 sample 的 MybatisPlusConfiguration。
     * 业务项目可覆盖此 Bean 添加更多 InnerInterceptor（如 TenantLineInnerInterceptor）。
     */
    @Bean
    // @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页（默认 MySQL 方言，业务项目可覆盖此 Bean 指定其他 DbType）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 乐观锁（配合 @Version 注解）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 防全表攻击（阻止无 WHERE 的 UPDATE/DELETE）
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }

}
