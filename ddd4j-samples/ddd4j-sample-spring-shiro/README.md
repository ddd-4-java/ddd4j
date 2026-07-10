# ddd4j-sample-spring-shiro

## 鉴权示例（Spring Boot + Shiro）三轨合一

ddd4j SubjectKit 统一鉴权入口在 Spring Boot 框架下的 Shiro 适配示例。

### 🎯 三轨合一展示

| 轨道                 | 模块                                   | 框架                 | 入口                 |
|--------------------|--------------------------------------|--------------------|--------------------|
| **一轨**：Auth 鉴权     | `AuthController / AuthService`       | Shiro (SubjectKit) | `POST /auth/login` |
| **二轨**：充血模型        | `Order 聚合 + OrderApplicationService` | Spring + ddd4j     | `POST /orders`     |
| **三轨**：Model/Query | `Goods PO + GoodsQuery`              | Spring + ddd4j     | `POST /api/goods`  |

> 🎯 业务代码（`AuthService`、`OrderApplicationService`、`Order`、`GoodsApplicationService`、`Goods`）在三个 Spring Auth
> 示例（satoken / shiro / security）中**逐字符一致**。

### 🎯 核心价值

本示例演示了 **ddd4j-auth 抽象层**与 **ddd4j 三轨模型**的双重核心优势：

- **业务代码零改动**：本示例与其他 6 个 Auth 示例（spring/Quarkus/Javalin × sa-token/shiro/security）的业务代码**完全一致**
- **切换底层鉴权框架只需改 pom.xml**：从 sa-token 切换到 shiro，只需替换 2 个依赖，业务代码不动
- **框架无关的鉴权契约**：业务代码统一通过 `SubjectKit` 调用，不直接依赖 sa-token/shiro/security API

### 📦 模块对应

| 框架          | 鉴权适配模块           | 桥接模块              |
|-------------|------------------|-------------------|
| Spring Boot | ddd4j-auth-shiro | ddd4j-auth-spring |

### 🚀 快速开始

**启动命令**：

```bash
mvn spring-boot:run    # Spring Boot
```

**端口**：8080

**测试登录**：

```bash
# 登录获取 Token
curl -X POST 'http://localhost:8080/auth/login?userId=10001'

# 查看当前用户
curl http://localhost:8080/auth/me

# 权限校验
curl 'http://localhost:8080/auth/check/permission?permission=user:add'
# 返回：{"permission":"user:add","has":true}

# 角色校验
curl 'http://localhost:8080/auth/check/role?role=admin'
# 返回：{"role":"admin","has":true}

# 踢人下线
curl -X POST 'http://localhost:8080/auth/kickout?userId=10002'

# 登录状态
curl http://localhost:8080/auth/status

# 登出
curl -X POST http://localhost:8080/auth/logout
```

**测试 Order 充血业务（第二轨）**：

```bash
# 1. 创建订单
curl -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"O-001","buyerId":"10001","buyerName":"张三"}'

# 2. 添加订单行（替换 {orderId} 为上一步返回的 id）
curl -X POST http://localhost:8080/orders/{orderId}/lines \
  -H 'Content-Type: application/json' \
  -d '{"goodsId":"SKU-001","goodsName":"iPhone 15","quantity":2,"unitPrice":5999.00}'

# 3. 支付订单
curl -X POST http://localhost:8080/orders/{orderId}/pay

# 4. 发货订单
curl -X POST http://localhost:8080/orders/{orderId}/ship

# 5. 取消订单
curl -X POST http://localhost:8080/orders/{orderId}/cancel

# 6. 查询订单详情
curl http://localhost:8080/orders/{orderId}

# 7. 按订单号查询
curl 'http://localhost:8080/orders/by-order-no?orderNo=O-001'

# 8. 列出全部订单
curl http://localhost:8080/orders

# 9. 预览折扣
curl http://localhost:8080/orders/{orderId}/discount
```

**测试 Goods CRUD（第三轨）**：

```bash
# 1. 创建商品
curl -X POST http://localhost:8080/api/goods \
  -H 'Content-Type: application/json' \
  -d '{"code":"SKU-001","name":"iPhone 15","price":5999.00,"stock":100}'

# 2. 更新商品
curl -X PUT http://localhost:8080/api/goods/1001 \
  -H 'Content-Type: application/json' \
  -d '{"name":"iPhone 15 Pro","price":7999.00}'

# 3. 调整状态（上架）
curl -X PUT 'http://localhost:8080/api/goods/1001/status?status=ON_SALE'

# 4. 查询商品
curl http://localhost:8080/api/goods/1001
curl 'http://localhost:8080/api/goods/by-code?code=SKU-001'

# 5. 充血分页查询（按状态过滤）
curl 'http://localhost:8080/api/goods/page?status=ON_SALE&current=1&size=10'

# 6. 充血列表查询
curl 'http://localhost:8080/api/goods/list?nameLike=iPhone'

# 7. 充血计数
curl 'http://localhost:8080/api/goods/count?status=ON_SALE'

# 8. 软删除
curl -X DELETE http://localhost:8080/api/goods/1001
```

### 📝 业务代码（AuthService.java）

```java
@Service
public class AuthService {
    public Map<String, Object> login(String userId) {
        AuthPrincipal principal = new AuthPrincipal()
                .setLoginId(userId).setUserId(userId).setRoleCode("user");
        AuthRequest request = AuthRequest.of(userId).setTimeout(7200);
        request.setPrincipal(principal);
        String token = SubjectKit.login(request);  // ← 统一入口
        // ...
    }
    // 其他方法使用 SubjectKit.logout() / getPrincipal() / hasPermission() / hasRole() 等
}
```

**关键点**：

- 本类**与其他 6 个示例完全一致**（证明切换框架业务代码零改动）
- 仅导入语句依赖 ddd4j-core（不依赖具体的 sa-token/shiro/security API）

### 🔄 切换鉴权框架

| 切换目标              | pom.xml 变化                     |
|-------------------|--------------------------------|
| Sa-Token → Shiro  | 替换 ddd4j-auth-{sa-token,shiro} |
| Shiro → Security  | 替换 ddd4j-auth-{shiro,security} |
| Spring → Quarkus  | ddd4j-runtime-{spring,quarkus} |
| Quarkus → Javalin | ddd4j-runtime-{quarkus,guice}  |

业务代码（`AuthController`、`AuthService`）**无需任何修改**。

### 🏗️ 架构图

```
┌─────────────────────────────────────────┐
│ 业务代码（AuthController/AuthService）  │ ← 本示例：业务代码完全一致
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
ddd4j-sample-spring-shiro/
├── pom.xml                              # 依赖配置
└── src/
    ├── main/java/io/ddd4j/sample/spring/shiro/
    │   ├── SpringShiroApplication.java          # 启动类
    │   ├── config/AuthConfig.java               # 鉴权配置
    │   ├── controller/AuthController.java
    │   ├── service/AuthService.java             # 鉴权业务代码（与其他示例一致）
    │   ├── order/                               # 🆕 充血业务（第二轨）
    │   │   ├── domain/model/{Order, OrderLine, OrderStatus, Money}.java
    │   │   ├── domain/event/{OrderCreatedEvent, OrderPaidEvent, OrderShippedEvent, OrderCancelledEvent, OrderLineAddedEvent}.java
    │   │   ├── domain/repository/OrderRepository.java
    │   │   ├── domain/service/OrderDomainService.java
    │   │   ├── application/{OrderApplicationService, CreateOrderCommand, AddOrderLineCommand}.java
    │   │   ├── infrastructure/InMemoryOrderRepository.java
    │   │   └── web/{OrderController, dto/{CreateOrderRequest, AddOrderLineRequest, OrderResponse}}.java
    │   └── goods/                             # 🆕 CRUD 业务（第三轨）
    │       ├── domain/{Goods, GoodsId, GoodsStatus, GoodsRepository, GoodsQuery}.java
    │       ├── application/GoodsApplicationService.java
    │       ├── infrastructure/InMemoryGoodsRepository.java
    │       └── web/{GoodsController, GoodsQueryController}.java
    ├── main/resources/
    │   └── application.yml
    └── test/java/io/ddd4j/sample/spring/shiro/
        └── AuthControllerTest.java
```

### 🔗 相关示例

- 同一框架的其他认证方案：`ddd4j-sample-spring-satoken`、`ddd4j-sample-spring-security`
- 其他框架的同认证方案：`ddd4j-sample-quarkus-shiro`、`ddd4j-sample-javalin-shiro`
- 核心 DDD 示例：`ddd4j-sample-spring`

### 📄 相关文档

- [ddd4j 主项目](https://github.com/hiwepy/ddd4j)
- [ddd4j-auth 模块文档](https://github.com/hiwepy/ddd4j/tree/main/ddd4j-auth)
- [SubjectKit API 文档](https://github.com/hiwepy/ddd4j/blob/main/ddd4j-core/src/main/java/io/ddd4j/core/util/SubjectKit.java)