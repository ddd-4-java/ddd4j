package io.ddd4j.boot.cmpt.cola.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.ddd4j.boot.cmpt.cola.handler.Ddd4jResponseHandler;

/**
 * COLA 架构组件自动配置。
 *
 * <p>当 classpath 存在 COLA 核心类时自动激活，提供：
 * <ul>
 *   <li>COLA Domain/Extension/CatchLog/StateMachine 组件的 Spring Boot 集成
 *       （这些组件自带 AutoConfiguration，通过 ComponentScan 激活）</li>
 *   <li>{@link Ddd4jResponseHandler} — 让 catchlog 的 ResponseHandler 输出 ddd4j 的 {@code ApiRestResponse} 格式</li>
 * </ul>
 *
 * <p>配置项（{@code application.yml}）：
 * <pre>
 * ddd4j:
 *   cola:
 *     enabled: true  # 默认开启，设为 false 可关闭 COLA 集成
 * </pre>
 *
 * <p>使用方式：在业务项目中引入本模块后，自动获得 COLA 的：
 * <ul>
 *   <li>{@code @Extension} 扩展点机制（多租户差异化业务逻辑）</li>
 *   <li>{@code @CatchAndLog} AOP 日志捕获</li>
 *   <li>StateMachine 状态机（用于订单状态流转等）</li>
 *   <li>DomainFactory 领域对象工厂</li>
 * </ul>
 *
 * @author wandl
 * @since 3.4.x
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.alibaba.cola.extension.Extension")
@ConditionalOnProperty(prefix = "ddd4j.cola", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = {"com.alibaba.cola"})
public class ColaAutoConfiguration {

    /**
     * 注册 ddd4j 风格的 ResponseHandler。
     *
     * <p>让 COLA catchlog 的异常处理输出 ddd4j 的 {@link io.ddd4j.boot.core.ApiRestResponse} 格式，
     * 而非 COLA 默认的 {@code Response} 格式。
     */
    @Bean
    @ConditionalOnMissingBean
    public Ddd4jResponseHandler ddd4jResponseHandler() {
        return new Ddd4jResponseHandler();
    }

}
