# Javadoc 补全批次报告 batch-14

模块：ddd4j-samples；文件数：30；Hazard 行：0

| 路径 | 处理符号数 | 状态 |
|---|---|---|
| ddd4j-samples/ddd4j-sample-javalin-cqrs/src/test/java/io/ddd4j/sample/javalin/cqrs/TestSupport.java | 3 | 已补 3（NoopDomainEventPublisher / NoopI18nProvider / NoopSubjectProvider） |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/JavalinSaTokenApplication.java | 1 | 已存在，跳过（PORT） |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/goods/domain/GoodsRepository.java | 2 | 已补 2（findByCode / findByStatus） |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/goods/domain/GoodsStatus.java | 4 | 已存在，跳过（4 个枚举常量） |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/order/domain/model/OrderStatus.java | 4 | 已存在，跳过（4 个枚举常量） |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/order/domain/repository/OrderRepository.java | 2 | 已补 2（findByOrderNo / findAll） |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/order/infrastructure/InMemoryOrderRepository.java | 2 | 已存在，跳过（OrderRow / OrderLineRow） |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/order/web/OrderResource.java | 1 | 已存在，跳过（DiscountView） |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/order/web/dto/OrderResponse.java | 1 | 已补 1（OrderLineResponse） |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/rbac/domain/model/Permission.java | 2 | 已补 1（ENABLED，顺带补 DISABLED）；enum Status 已存在跳过 |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/rbac/domain/model/Role.java | 2 | 已补 1（ENABLED，顺带补 DISABLED）；enum Status 已存在跳过 |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/rbac/domain/model/User.java | 2 | 已补 1（ENABLED，顺带补 DISABLED）；enum Status 已存在跳过 |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/rbac/domain/repository/PermissionRepository.java | 3 | 已存在，跳过 |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/rbac/domain/repository/RoleRepository.java | 2 | 已存在，跳过 |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/rbac/domain/repository/UserRepository.java | 3 | 已存在，跳过 |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/rbac/infrastructure/InMemoryPermissionRepository.java | 1 | 已存在，跳过（PermissionRow） |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/rbac/infrastructure/InMemoryRoleRepository.java | 1 | 已存在，跳过（RoleRow） |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/rbac/infrastructure/InMemoryUserRepository.java | 1 | 已存在，跳过（UserRow） |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/rbac/web/AuthenticationController.java | 4 | 已补 4（LoginRequest / KickoutRequest / RoleCheckRequest / PermissionCheckRequest） |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/main/java/io/ddd4j/sample/javalin/satoken/rbac/web/AuthorizationController.java | 8 | 已补 8（CreateUserRequest / UpdateUserRequest / AssignRolesRequest / CreateRoleRequest / UpdateRoleRequest / AssignPermissionsRequest / CreatePermissionRequest / UpdatePermissionRequest） |
| ddd4j-samples/ddd4j-sample-javalin-satoken/src/test/java/io/ddd4j/sample/javalin/satoken/TestSupport.java | 2 | 已补 2（NoopDomainEventPublisher / NoopI18nProvider） |
| ddd4j-samples/ddd4j-sample-javalin-shiro/src/main/java/io/ddd4j/sample/javalin/shiro/JavalinShiroApplication.java | 3 | 已补 2（NoOpDomainEventPublisher / DefaultI18nProvider）；PORT 已存在跳过 |
| ddd4j-samples/ddd4j-sample-javalin-shiro/src/main/java/io/ddd4j/sample/javalin/shiro/config/AuthConfig.java | 1 | 已存在，跳过（RbacRealm） |
| ddd4j-samples/ddd4j-sample-javalin-shiro/src/main/java/io/ddd4j/sample/javalin/shiro/goods/domain/GoodsRepository.java | 2 | 已补 2（findByCode / findByStatus） |
| ddd4j-samples/ddd4j-sample-javalin-shiro/src/main/java/io/ddd4j/sample/javalin/shiro/goods/domain/GoodsStatus.java | 4 | 已存在，跳过（4 个枚举常量） |
| ddd4j-samples/ddd4j-sample-javalin-shiro/src/main/java/io/ddd4j/sample/javalin/shiro/order/domain/model/OrderStatus.java | 4 | 已存在，跳过（4 个枚举常量） |
| ddd4j-samples/ddd4j-sample-javalin-shiro/src/main/java/io/ddd4j/sample/javalin/shiro/order/domain/repository/OrderRepository.java | 2 | 已补 2（findByOrderNo / findAll） |
| ddd4j-samples/ddd4j-sample-javalin-shiro/src/main/java/io/ddd4j/sample/javalin/shiro/order/infrastructure/InMemoryOrderRepository.java | 2 | 已存在，跳过（OrderRow / OrderLineRow） |
| ddd4j-samples/ddd4j-sample-javalin-shiro/src/main/java/io/ddd4j/sample/javalin/shiro/order/web/OrderResource.java | 1 | 已存在，跳过（DiscountView） |
| ddd4j-samples/ddd4j-sample-javalin-shiro/src/main/java/io/ddd4j/sample/javalin/shiro/order/web/dto/OrderResponse.java | 1 | 已补 1（OrderLineResponse） |

批次合计：30 个文件 | 71 个清单符号（新增 javadoc 32 个，已存在跳过 39 个；另顺带补齐 3 个未列清单的 DISABLED 枚举常量注释）| 复查结果 30/30 全部通过
