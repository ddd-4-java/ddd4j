package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 领域模型标记-领域网关接口（纯 Java 注解，零框架依赖）。
 *
 * <p>标记仓储接口或外部服务接口（防腐层 ACL），与 {@link DomainRepository} 区分：
 * <ul>
 *   <li>{@code @DomainGateway} — 标记<strong>接口</strong>，放在 {@code domain.gateway} 包</li>
 *   <li>{@code @DomainRepository} — 标记<strong>实现类</strong>，放在 {@code adapter.persistence} 包</li>
 * </ul>
 *
 * <p>COLA 架构中，{@code domain.gateway} 包含仓储接口和外部系统接口，
 * {@code adapter.persistence} 包含其实现（依赖倒置）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see DomainRepository
 * @since 3.4.x
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface DomainGateway {
}
