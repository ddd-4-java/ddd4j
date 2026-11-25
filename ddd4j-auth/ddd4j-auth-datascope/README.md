# Spring 数据权限校验集成

本模块保留原有 Maven jar 依赖入口，传递引入 Easy4j 权限契约、Spring Context 和 Hibernate Validator。生产校验实现全部复用组件，不再发布 DDD4J 的同名注解、Provider、Validator 或默认策略配置。

应用直接使用 `io.github.easy4j.validation.constraints.RequiresDataPermissions`，并注册 `io.github.easy4j.validation.provider.DataScopeProvider` Bean。

```java
@Bean
public DataScopeProvider orderPermissionProvider(OrderPermissionService service) {
    return service::hasPermissions;
}
```

`OrderPermissionService` 是应用自己的业务权限服务。使用 Spring 标准 `LocalValidatorFactoryBean`，其默认 `SpringConstraintValidatorFactory` 会执行标准 Inject 注入。Spring Boot/MVC 已有 Validator 时直接复用；普通 Spring 应用按常规注册 LocalValidatorFactoryBean 即可，无需自定义权限工厂或静态上下文。

使用权限约束时，缺失 Provider 必须报错；多个候选且未声明 Primary/唯一候选时也报错。配置后的业务拒绝保持标准约束违规，策略异常传播。旧 DataScopeAutoConfiguration 与 nonNullAllowed 默认策略已移除，迁移时更新 import、移除旧配置导入并重新编译。

真实容器用例见 DataPermissionSpringIntegrationTest：允许/拒绝/null、标准约束并用、缺失 Provider、候选歧义和两个容器的策略隔离。
