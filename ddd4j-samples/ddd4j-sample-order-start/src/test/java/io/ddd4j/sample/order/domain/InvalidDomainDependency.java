package io.ddd4j.sample.order.domain;

import io.ddd4j.sample.order.app.CatalogService;

/**
 * 故意依赖应用服务的领域类，用于验证 COLA 规则能够拒绝反向依赖。
 */
public interface InvalidDomainDependency {
    /**
     * 暴露应用服务类型，使编译后的类保留违规依赖。
     *
     * @return 领域层不应引用的应用服务
     */
    CatalogService service();
}
