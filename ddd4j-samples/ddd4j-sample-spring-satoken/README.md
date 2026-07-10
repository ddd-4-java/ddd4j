# ddd4j-sample-spring-satoken

## 完整 RBAC 示例（Spring Boot + Sa-Token）— 三轨合一

ddd4j SubjectKit 统一鉴权入口在 Spring Boot 框架下的 Sa-Token 适配示例 + 完整 RBAC（Role-Based Access Control）业务演示。

### 🎯 三轨合一展示

| 轨道               | 模块                                   | 框架                 | 入口                  |
|------------------|--------------------------------------|--------------------|---------------------|
| **一轨**：RBAC 授权管理 | `AuthorizationController`            | ddd4j + Spring MVC | `POST /admin/users` |
| **一轨**：RBAC 鉴权操作 | `AuthenticationController`           | Sa-Token 注解式鉴权     | `POST /auth/login`  |
| **二轨**：充血业务      | `Order 聚合 + OrderApplicationService` | Spring + ddd4j     | `POST /orders`      |
| **三轨**：CRUD 业务   | `Goods PO + GoodsQuery`              | Spring + ddd4j     | `POST /api/goods`   |

> 🎯 业务代码（`RbacService` / `AuthorizationController` / `AuthenticationController` / `Order` / `Goods`）在三个 Spring
> Auth 示例（satoken / shiro / security）中**逐字符一致**。
> 仅认证框架配置（`RbacConfig` 中注册的 `SubjectDataProvider`）和启动类不同。

### 🎯 RBAC 核心价值

本示例演示了**完整的 RBAC（用户-角色-权限）** 业务模型 + 三种鉴权控制：

- **登录鉴权**（`@SaCheckLogin`）：账号密码登录 + Token
- **角色鉴权**（`@SaCheckRole("admin")`）：基于角色（admin/manager/user）的 API 访问控制
- **权限鉴权**（`@SaCheckPermission("user:add")`）：基于 permission code 的 API 访问控制
- **业务接口权限鉴权**：订单支付接口需要 `order:pay` 权限

### 📦 预置演示数据

#### 用户（3 个）

| 用户名        | 密码       | 用户 ID | 角色    | 状态       |
|------------|----------|-------|-------|----------|
| `admin`    | `123456` | 10001 | admin | ENABLED  |
| `user`     | `123456` | 10002 | user  | ENABLED  |
| `disabled` | `123456` | 10003 | user  | DISABLED |

#### 角色（3 个）

| 角色编码      | 角色名   | 包含权限                                                                      |
|-----------|-------|---------------------------------------------------------------------------|
| `admin`   | 超级管理员 | user:add, user:delete, user:list, role:add, goods:view, order:pay（全部 6 个） |
| `user`    | 普通用户  | user:list, goods:view                                                     |
| `manager` | 业务管理员 | user:list, goods:view, order:pay                                          |

#### 权限（6 个）

| 权限编码          | 权限名  | 模块    |
|---------------|------|-------|
| `user:add`    | 新增用户 | user  |
| `user:delete` | 删除用户 | user  |
| `user:list`   | 查询用户 | user  |
| `role:add`    | 新增角色 | role  |
| `goods:view`  | 查看商品 | goods |
| `order:pay`   | 订单支付 | order |

### 📦 模块对应

| 框架          | 鉴权适配模块              | 桥接模块              |
|-------------|---------------------|-------------------|
| Spring Boot | ddd4j-auth-sa-token | ddd4j-auth-spring |

### 🚀 快速开始

**启动命令**：

```bash
mvn spring-boot:run    # Spring Boot
```

**端口**：8080

### 🎯 鉴权操作 API（`/auth/*`）

#### 1) 登录鉴权（账号密码登录）

```bash
# 登录获取 Token
curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}'
# 返回：{"success":true,"token":"xxx","tokenName":"ddd4j-token"}

# 登出
curl -X POST http://localhost:8080/auth/logout

# 当前登录用户
curl http://localhost:8080/auth/me
# 返回：{"authenticated":true,"userId":"10001","userCode":"admin","roleCode":"admin",...}

# 登录状态
curl http://localhost:8080/auth/status

# 踢人下线
curl -X POST http://localhost:8080/auth/kickout \
  -H 'Content-Type: application/json' \
  -d '{"userId":"10002"}'
```

#### 2) 角色鉴权（`@SaCheckRole`）

```bash
# 检查是否拥有某个角色（编程式，需先登录）
TOKEN="xxx"
curl -X POST http://localhost:8080/auth/check/role \
  -H "ddd4j-token: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"role":"admin"}'
# 返回：{"role":"admin","has":true}

# 仅 admin 角色可访问（注解式，后端强制拦截）
curl -H "ddd4j-token: $TOKEN" http://localhost:8080/auth/admin

# 仅 manager 角色可访问
curl -H "ddd4j-token: $TOKEN" http://localhost:8080/auth/manager
```

#### 3) 权限鉴权（`@SaCheckPermission`）

```bash
# 检查是否拥有某权限（编程式，需先登录）
curl -X POST http://localhost:8080/auth/check/permission \
  -H "ddd4j-token: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"permission":"user:delete"}'
# 返回：{"permission":"user:delete","has":true}

# 需要 user:list 权限（注解式）
curl -H "ddd4j-token: $TOKEN" http://localhost:8080/auth/users

# 业务接口权限鉴权：订单支付需要 order:pay 权限
curl -X POST -H "ddd4j-token: $TOKEN" http://localhost:8080/auth/orders/ORDER-001/pay
```

#### 4) 组合鉴权（角色 + 权限 AND）

```bash
# 需要 admin 角色 + user:delete 权限（两个条件都满足）
curl -X DELETE -H "ddd4j-token: $TOKEN" http://localhost:8080/auth/users/10002
```

### 🎯 授权管理 API（`/admin/*`）

#### 用户管理

```bash
# 列表查询
curl http://localhost:8080/admin/users

# 详情
curl http://localhost:8080/admin/users/10001

# 创建用户
curl -X POST http://localhost:8080/admin/users \
  -H 'Content-Type: application/json' \
  -d '{"userId":"10004","username":"newuser","password":"123456","realName":"新用户"}'

# 更新用户
curl -X PUT http://localhost:8080/admin/users/10004 \
  -H 'Content-Type: application/json' \
  -d '{"realName":"新名字","status":"ENABLED"}'

# 删除用户
curl -X DELETE http://localhost:8080/admin/users/10004

# 分配角色
curl -X POST http://localhost:8080/admin/users/10002/roles \
  -H 'Content-Type: application/json' \
  -d '{"roleIds":["R002","R003"]}'

# 查询用户所有权限（含角色继承）
curl http://localhost:8080/admin/users/10001/permissions
# 返回：{"userId":"10001","roles":["admin"],"permissions":["user:add","user:delete",...]}
```

#### 角色管理

```bash
# 列表查询
curl http://localhost:8080/admin/roles

# 详情
curl http://localhost:8080/admin/roles/R001

# 创建角色
curl -X POST http://localhost:8080/admin/roles \
  -H 'Content-Type: application/json' \
  -d '{"roleId":"R004","roleCode":"vip","roleName":"VIP 用户","description":"VIP"}'

# 更新角色
curl -X PUT http://localhost:8080/admin/roles/R004 \
  -H 'Content-Type: application/json' \
  -d '{"roleName":"VIP 用户（更新）","status":"ENABLED"}'

# 删除角色
curl -X DELETE http://localhost:8080/admin/roles/R004

# 给角色分配权限
curl -X POST http://localhost:8080/admin/roles/R002/permissions \
  -H 'Content-Type: application/json' \
  -d '{"permissionIds":["P003","P005","P006"]}'

# 查询角色权限
curl http://localhost:8080/admin/roles/R002/permissions
```

#### 权限管理

```bash
# 列表查询
curl http://localhost:8080/admin/permissions

# 详情
curl http://localhost:8080/admin/permissions/P001

# 创建权限
curl -X POST http://localhost:8080/admin/permissions \
  -H 'Content-Type: application/json' \
  -d '{"permissionId":"P007","permissionCode":"order:refund","permissionName":"订单退款","module":"order"}'

# 更新权限
curl -X PUT http://localhost:8080/admin/permissions/P007 \
  -H 'Content-Type: application/json' \
  -d '{"permissionName":"订单退款（更新）","module":"order"}'

# 删除权限
curl -X DELETE http://localhost:8080/admin/permissions/P007
```

### 📦 Order 充血业务（第二轨）

```bash
# 创建订单
curl -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"O-001","buyerId":"10001","buyerName":"张三"}'

# 添加订单行（替换 {orderId}）
curl -X POST http://localhost:8080/orders/{orderId}/lines \
  -H 'Content-Type: application/json' \
  -d '{"goodsId":"SKU-001","goodsName":"iPhone 15","quantity":2,"unitPrice":5999.00}'

# 支付订单
curl -X POST http://localhost:8080/orders/{orderId}/pay

# 发货订单
curl -X POST http://localhost:8080/orders/{orderId}/ship

# 取消订单
curl -X POST http://localhost:8080/orders/{orderId}/cancel

# 查询订单详情
curl http://localhost:8080/orders/{orderId}

# 按订单号查询
curl 'http://localhost:8080/orders/by-order-no?orderNo=O-001'

# 列出全部订单
curl http://localhost:8080/orders

# 预览折扣
curl http://localhost:8080/orders/{orderId}/discount
```

### 📦 Goods CRUD（第三轨）

```bash
# 创建商品
curl -X POST http://localhost:8080/api/goods \
  -H 'Content-Type: application/json' \
  -d '{"code":"SKU-001","name":"iPhone 15","price":5999.00,"stock":100}'

# 更新商品
curl -X PUT http://localhost:8080/api/goods/1001 \
  -H 'Content-Type: application/json' \
  -d '{"name":"iPhone 15 Pro","price":7999.00}'

# 调整状态
curl -X PUT 'http://localhost:8080/api/goods/1001/status?status=ON_SALE'

# 查询商品
curl http://localhost:8080/api/goods/1001
curl 'http://localhost:8080/api/goods/by-code?code=SKU-001'

# 充血分页查询
curl 'http://localhost:8080/api/goods/page?status=ON_SALE&current=1&size=10'

# 充血列表查询
curl 'http://localhost:8080/api/goods/list?nameLike=iPhone'

# 充血计数
curl 'http://localhost:8080/api/goods/count?status=ON_SALE'

# 软删除
curl -X DELETE http://localhost:8080/api/goods/1001
```

### 📝 关键代码

#### RbacService.java（业务核心）

```java
@Service
public class RbacService implements SubjectDataProvider {

    /** 登录：校验用户名/密码并建立会话 */
    public String login(String username, String password) {
        // 1. 查询用户
        // 2. 校验密码 & 状态
        // 3. 构造 AuthPrincipal
        // 4. 调用 SubjectKit.login(request) 统一入口
        return SubjectKit.login(request);
    }

    /** 鉴权便捷门面：转发到 SubjectKit */
    public boolean hasRole(String roleCode)    { return SubjectKit.hasRole(roleCode); }
    public boolean hasPermission(String perm)  { return SubjectKit.hasPermission(perm); }

    /** SubjectDataProvider：从 RBAC 存储聚合用户的角色和权限 */
    @Override
    public List<String> getPermissionList(AuthPrincipal principal) {
        // 从 user → roleIds → permissionIds → permissionCodes
    }
}
```

**关键点**：

- 本类**与其他 6 个 RBAC 示例完全一致**（切换框架业务代码零改动）
- 同时实现 `SubjectDataProvider`，由 `RbacConfig` 注册为 `SubjectKit.setDataProvider`
- 仅依赖 ddd4j-core，不依赖 sa-token/shiro/security API

#### AuthenticationController.java（鉴权操作）

```java
@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) { ... }

    @SaCheckRole("admin")
    @GetMapping("/admin")
    public Map<String, Object> adminOnly() { ... }   // 后端强制角色拦截

    @SaCheckPermission("order:pay")
    @PostMapping("/orders/{id}/pay")
    public Map<String, Object> payOrder(@PathVariable String id) { ... }  // 业务接口鉴权

    @SaCheckRole(value = "admin", mode = SaMode.AND)
    @SaCheckPermission("user:delete")
    @DeleteMapping("/users/{id}")
    public Map<String, Object> deleteUser(...) { ... }  // 组合鉴权
}
```

### 🔄 切换鉴权框架

| 切换目标              | pom.xml 变化                     |
|-------------------|--------------------------------|
| Sa-Token → Shiro  | 替换 ddd4j-auth-{sa-token,shiro} |
| Shiro → Security  | 替换 ddd4j-auth-{shiro,security} |
| Spring → Quarkus  | ddd4j-runtime-{spring,quarkus} |
| Quarkus → Javalin | ddd4j-runtime-{quarkus,guice}  |

业务代码（`RbacService` / `AuthorizationController` / `AuthenticationController`）**无需任何修改**。

### 🏗️ 架构图

```
┌─────────────────────────────────────────┐
│ 业务代码（Authorization/Authentication/ │
│         RbacService）                   │ ← 本示例：业务代码完全一致
├─────────────────────────────────────────┤
│ ddd4j SubjectKit 统一鉴权入口            │ ← 框架无关
├─────────────────────────────────────────┤
│ ddd4j-auth-{sa-token|shiro|security}    │ ← 鉴权适配层
├─────────────────────────────────────────┤
│ ddd4j-auth-{spring|quarkus|guice}       │ ← 框架桥接层
├─────────────────────────────────────────┤
│ Sa-Token / Shiro / Spring Security      │ ← 具体鉴权框架
└─────────────────────────────────────────┘
```

### 📁 项目结构

```
ddd4j-sample-spring-satoken/
├── pom.xml                              # 依赖配置
└── src/
    ├── main/java/io/ddd4j/sample/spring/satoken/
    │   ├── SpringSaTokenApplication.java         # 启动类
    │   ├── config/RbacConfig.java                # RBAC 配置 + SubjectDataProvider 注册
    │   ├── rbac/                                # 🆕 完整 RBAC（一轨）
    │   │   ├── domain/model/{User, Role, Permission}.java
    │   │   ├── domain/repository/{UserRepository, RoleRepository, PermissionRepository}.java
    │   │   ├── infrastructure/InMemory{User,Role,Permission}Repository.java
    │   │   ├── application/RbacService.java              # 业务核心（与其他示例一致）
    │   │   └── web/{AuthorizationController, AuthenticationController}.java
    │   ├── order/                                # 充血业务（第二轨）
    │   │   ├── domain/{Order, OrderLine, OrderStatus, Money}.java
    │   │   ├── domain/event/{OrderCreatedEvent, OrderPaidEvent, ...}.java
    │   │   ├── domain/repository/OrderRepository.java
    │   │   ├── domain/service/OrderDomainService.java
    │   │   ├── application/{OrderApplicationService, CreateOrderCommand, AddOrderLineCommand}.java
    │   │   ├── infrastructure/InMemoryOrderRepository.java
    │   │   └── web/{OrderController, dto/...}.java
    │   └── goods/                              # CRUD 业务（第三轨）
    │       ├── domain/{Goods, GoodsId, GoodsStatus, GoodsRepository, GoodsQuery}.java
    │       ├── application/GoodsApplicationService.java
    │       ├── infrastructure/InMemoryGoodsRepository.java
    │       └── web/{GoodsController, GoodsQueryController}.java
    ├── main/resources/
    │   └── application.yml
    └── test/java/io/ddd4j/sample/spring/satoken/
        └── AuthControllerTest.java
```

### 🔗 相关示例

- 同一框架的其他认证方案：`ddd4j-sample-spring-shiro`、`ddd4j-sample-spring-security`
- 其他框架的同认证方案：`ddd4j-sample-quarkus-satoken`、`ddd4j-sample-javalin-satoken`
- 核心 DDD 示例：`ddd4j-sample-spring`

### 📄 相关文档

- [ddd4j 主项目](https://github.com/hiwepy/ddd4j)
- [ddd4j-auth 模块文档](https://github.com/hiwepy/ddd4j/tree/main/ddd4j-auth)
- [SubjectKit API 文档](https://github.com/hiwepy/ddd4j/blob/main/ddd4j-core/src/main/java/io/ddd4j/core/util/SubjectKit.java)
- [sa-token 官方文档](https://sa-token.dev33.cn/)