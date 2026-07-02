# ddd4j 示例工程

本目录放框架级、纯 Java 或最小运行时依赖的示例。Boot / Javalin / Quarkus 的框架接入示例分别放在各自仓库的 `*-samples` 下。

## 示例清单

| 示例                             | 方向           | 说明 |
|--------------------------------|--------------|------|
| `ddd4j-sample-rich-model`      | 普通充血模型      | `AggregateRoot` + `DomainRepository` + Model/PO 分离，领域模型零 ORM/框架依赖 |
| `ddd4j-sample-cqrs-person`     | CQRS / ES    | 纯 Java Person CQRS 示例：命令写侧、事件流、读侧投影与序列化测试 |
| `ddd4j-sample-auth-multi-login` | 多登录场景       | 手机号登录、第三方登录、登录成功/失败事件与 `SubjectKit` 用法 |
| `ddd4j-sample-auth-satoken`    | Auth         | sa-token 集成示例（主推方案） |
| `ddd4j-sample-auth-shiro`      | Auth         | Apache Shiro 集成示例（旧项目迁移参考） |
| `ddd4j-sample-auth-security`   | Auth         | Spring Security 集成示例（旧项目迁移参考） |

## 普通充血模型示例

`ddd4j-sample-rich-model` 是后续 Boot / Javalin / Quarkus rich-model 示例的共同领域基准：

- 领域模型：`Order` 继承 `AggregateRoot<String>`，内部封装 `addLine`、`pay`、`ship` 等业务行为。
- 值对象：`Money` 实现 `ValueObject`，负责金额和币种不变式。
- 领域事件：`OrderCreatedEvent`、`OrderLineAddedEvent`、`OrderPaidEvent` 等在聚合行为内注册。
- 仓储接口：`OrderRepository` 继承 `DomainRepository<Order, String>`，只描述聚合语义。
- 基础设施：`InMemoryOrderRepository` 实现 `DomainObjectMapper<Order, OrderPO>`，演示 Model/PO 分离。

验证命令：

```bash
mvn -pl ddd4j-samples/ddd4j-sample-rich-model -am test -DskipTests=false
```

## 核心演示：三框架业务代码零差异

三个 auth 示例的 `AuthController` / `AuthConfig` 代码**完全一致**，只有 pom 依赖不同：

```java
// 三个示例完全相同的业务代码
AuthPrincipal principal = new AuthPrincipal().setLoginId(userId).setUserId(userId);
AuthRequest request = AuthRequest.of(userId).setTimeout(7200);
request.setPrincipal(principal);
String token = SubjectKit.login(request);       // 登录

SubjectKit.hasPermission("user:add");            // 权限校验
SubjectKit.hasRole("admin");                     // 角色校验
SubjectKit.getPrincipal();                        // 获取当前用户
SubjectKit.logout();                              // 登出
```

切换底层鉴权框架（sa-token ↔ shiro ↔ security）只需更换 pom 依赖，**业务代码零改动**。

## 快速开始

### sa-token 示例（主推）

```bash
cd ddd4j-samples/ddd4j-sample-auth-satoken
mvn spring-boot:run
# 访问 http://localhost:8080/auth/login?userId=10001
```

### Shiro 示例（旧项目迁移参考）

```bash
cd ddd4j-samples/ddd4j-sample-auth-shiro
mvn spring-boot:run
# 访问 http://localhost:8081/auth/login?userId=10001
```

### Spring Security 示例（旧项目迁移参考）

```bash
cd ddd4j-samples/ddd4j-sample-auth-security
mvn spring-boot:run
# 访问 http://localhost:8082/auth/login?userId=10001
```

## 体验鉴权流程

```bash
# 登录（获取 Token）
curl -X POST 'http://localhost:8080/auth/login?userId=10001'

# 查看当前用户
curl http://localhost:8080/auth/me

# 权限校验（user:add 权限）
curl 'http://localhost:8080/auth/check/permission?permission=user:add'
# 返回：{"permission":"user:add","has":true}

# 权限校验（无 user:update 权限）
curl 'http://localhost:8080/auth/check/permission?permission=user:update'
# 返回：{"permission":"user:update","has":false}

# 角色校验（admin 角色）
curl 'http://localhost:8080/auth/check/role?role=admin'
# 返回：{"role":"admin","has":true}

# 登出
curl -X POST http://localhost:8080/auth/logout
```

## 迁移指南

完整的 Shiro → sa-token、Spring Security → sa-token 迁移指南见：
[`ddd4j-auth/docs/migration-guide.md`](../ddd4j-auth/docs/migration-guide.md)
