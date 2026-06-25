package io.ddd4j.boot.cmpt.dubbo.config;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Dubbo 自动配置。
 *
 * <p>当 classpath 存在 Dubbo 核心类时自动激活，提供：
 * <ul>
 *   <li>Dubbo Service/Reference 注解扫描支持（通过 {@link EnableDubbo}）</li>
 *   <li>Dubbo Actuator 健康检查集成</li>
 *   <li>Dubbo 异常到 {@link io.ddd4j.boot.core.ApiRestResponse} 的统一转换</li>
 * </ul>
 *
 * <p>配置项（{@code application.yml}）：
 * <pre>
 * dubbo:
 *   application:
 *     name: ${spring.application.name}
 *   registry:
 *     address: nacos://127.0.0.1:8848
 *   protocol:
 *     name: dubbo
 *     port: 20880
 *   scan:
 *     base-packages: com.example.provider  # Dubbo Service 扫描包
 * </pre>
 *
 * <p>可通过 {@code ddd4j.dubbo.enabled=false} 关闭自动配置。
 *
 * @author wandl
 * @since 3.4.x
 */
@Configuration(proxyBeanMethods = false)
// @ConditionalOnClass(name = "org.apache.dubbo.config.spring.context.annotation.EnableDubbo")
public class DubboAutoConfiguration {

}
