# Javalin Shiro 示例

## 当前商品交易入口

此模块以 Shiro 方式运行共享商品交易。先按[示例总览](../README.md#启动默认订单示例)配置 MySQL。以下命令从仓库根目录运行，使用实际 Maven 版本替换 `<version>`。

```bash
module=ddd4j-samples/ddd4j-sample-javalin-shiro
./mvnw -pl "$module" -am -DskipTests -Dmaven.javadoc.skip=true -DincludeScope=runtime -Dmdep.outputFile=target/runtime-classpath.txt package dependency:build-classpath
java -cp "$module/target/ddd4j-sample-javalin-shiro-<version>.jar:$(cat "$module/target/runtime-classpath.txt")" io.ddd4j.sample.javalin.shiro.JavalinShiroApplication
```

业务请求携带 `X-Tenant-Id`。商品地址为 `/orders/catalog/goods/{spuCode}`，订单地址为 `/orders/catalog/orders/{orderNo}`。支付、取消和履约分别使用订单子路径 `/payment`、`/cancel`、`/fulfill`。支付请求必须提供非空 `Idempotency-Key`，重试时复用原键和实付金额。

本地演示登录使用 `POST /auth/login`，请求体为 `{"loginId":"zhangsan","password":"pass123"}`。将令牌放入 `ddd4j-token: <token>` 请求头。

<details>
<summary>商品交易迁移前的说明</summary>

示例在 Guice 中装配 Shiro、`SubjectKit` 和共享订单服务。订单端点位于 `/orders`，支付和取消分别按 `order:pay`、`order:cancel` 检查权限。

## 构建和启动

在仓库根目录使用当前发行线的 JDK 和 Maven Wrapper。将 `<version>` 换成 `target` 中实际 JAR 的版本。主入口使用端口 `7000`，一次启动一个认证示例。

```bash
module=ddd4j-samples/ddd4j-sample-javalin-shiro
./mvnw -pl "$module" -am -Dmaven.test.skip=true -Dmaven.javadoc.skip=true \
  -DincludeScope=runtime -Dmdep.outputFile=target/runtime-classpath.txt \
  package dependency:build-classpath
java -cp "$module/target/ddd4j-sample-javalin-shiro-<version>.jar:$(cat "$module/target/runtime-classpath.txt")" \
  io.ddd4j.sample.javalin.shiro.JavalinShiroApplication
```

## 登录、读取主体和退出

本地演示账号为 `zhangsan / pass123`。登录返回的 `data.token` 用于后续请求，请将下方 `<token>` 替换为实际值。

```bash
base=http://127.0.0.1:7000
curl -fsS -X POST "$base/auth/login" -H "Content-Type: application/json" \
  -d '{"loginId":"zhangsan","password":"pass123"}'
curl -fsS "$base/auth/me" -H "ddd4j-token: <token>"
curl -fsS -X POST "$base/auth/logout" -H "ddd4j-token: <token>"
```

`GET /auth/me` 返回当前登录主体。退出后，用同一令牌请求 `/auth/me` 返回 401。

启用商品交易时，`reader / sample-password` 可读取商品与订单，写入返回 403。

## 请求和资源生命周期

主入口 `main` 调用可关闭的 `start(int)` 装配 Javalin 和 Guice。同一个 JVM 中使用单个活动实例并配对启动和关闭。订单和认证演示数据存放在内存中。

Shiro Realm 负责凭证、角色和权限。Servlet 请求结束时清除本次 Shiro 绑定，并恢复原主体和 ddd4j `ThreadContext`。

原生 `NativeAuthHttpTest` 覆盖登录、订单操作、拒绝和退出，另有用例验证请求清理与启动失败恢复。

<details>
<summary>早期鉴权设计记录</summary>

以下保留早期设计和示意代码。当前版本的启动和登录方式以上文为准。

## 鉴权示例（Javalin + Guice + Apache Shiro）

ddd4j SubjectKit 统一鉴权入口在 Javalin 框架下的 Apache Shiro 适配示例。

> 与 `ddd4j-sample-javalin-satoken` 业务代码 **完全一致**，仅 `pom.xml` 和 `AuthConfig` 不同。
> 本示例证明：在 Javalin 框架下从 Sa-Token 切换到 Apache Shiro，业务层 0 改动。

---

## 🎯 核心价值

本示例演示了 **ddd4j-auth 抽象层**的核心优势：

- **业务代码零改动**：本示例的 `AuthService` / `AuthController` 与 `spring-shiro` / `quarkus-shiro` / `javalin-satoken`
  等其他 6 个示例 **完全一致**
- **切换底层鉴权框架只需改 pom.xml**：从 Sa-Token 切换到 Shiro，只需替换 2 个依赖，业务代码不动
- **框架无关的鉴权契约**：业务代码统一通过 `SubjectKit` 调用，不直接依赖 sa-token / shiro / security API

---

## 📦 模块对应

| 框架    | 鉴权适配模块       | 运行时桥接模块        | 鉴权框架     |
|---------|--------------------|-----------------------|--------------|
| Javalin | `ddd4j-auth-shiro` | `ddd4j-runtime-guice` | Apache Shiro |

> Javalin 本身 **没有 DI 容器**，所以 `ddd4j-runtime-guice` 同时承担两件事：
> 1. 提供 Guice 作为 IoC 容器（替代 Spring / CDI）
> 2. 提供 Javalin 路由所需的 web 工具（`WebKit` / `JavalinMDCPlugin` 等）
>
> 4 个核心 SPI 仍需业务方在 `main()` 中 **手动** `BaseContext.inject(...)`——这是 Javalin 框架特有的初始化步骤。

---

## 🚀 快速开始

### 启动命令

```bash
# 方式 1：通过 exec-maven-plugin 启动
mvn -pl ddd4j-samples/ddd4j-sample-javalin-shiro exec:java \
    -Dexec.mainClass=io.ddd4j.sample.javalin.shiro.JavalinShiroApplication

# 方式 2：先 package 再 java -jar
mvn -pl ddd4j-samples/ddd4j-sample-javalin-shiro package
java -jar ddd4j-samples/ddd4j-sample-javalin-shiro/target/ddd4j-sample-javalin-shiro-*.jar
```

**端口**：`7000`

### 测试命令（curl 示例）

```bash
# 登录获取 Token
curl -X POST 'http://localhost:7000/auth/login?userId=10001'

# 查看当前用户
curl 'http://localhost:7000/auth/me'

# 权限校验
curl 'http://localhost:7000/auth/check/permission?permission=user:add'
# 返回：{"permission":"user:add","has":true}

# 角色校验
curl 'http://localhost:7000/auth/check/role?role=admin'
# 返回：{"role":"admin","has":true}

# 踢人下线
curl -X POST 'http://localhost:7000/auth/kickout?userId=10001'

# 登录状态
curl 'http://localhost:7000/auth/status'
# 返回：{"login":false}
```

---

## 🆚 与 Sa-Token 版对比（`ddd4j-sample-javalin-satoken`）

| 文件             | Sa-Token 版                              | Shiro 版（本示例）                                    | 差异                  |
|------------------|------------------------------------------|-------------------------------------------------------|-----------------------|
| `pom.xml`        | `ddd4j-auth-satoken` + 不需要 shiro-core | `ddd4j-auth-shiro` + `shiro-core 2.0.6`               | 依赖替换              |
| 启动类           | `JavalinSaTokenApplication`（端口 8085） | `JavalinShiroApplication`（端口 7000）                | 启动端口 + 引导 Shiro |
| `AuthConfig`     | 无                                       | 新增（`AuthConfig.initShiro()` 引导 SecurityManager） | Shiro 特有步骤        |
| `AuthService`    | **完全一致**                             | **完全一致**                                          | 0 改动                |
| `AuthController` | **完全一致**                             | **完全一致**                                          | 0 改动                |
| `AuthModule`     | **完全一致**                             | **完全一致**                                          | 0 改动                |

> 业务代码（`AuthService` / `AuthController` / `AuthModule` 中的 `SubjectDataProvider`）与 Sa-Token 版 **逐字符一致**。
> 唯一多出的代码是 `AuthConfig.initShiro()`，仅与 Shiro SecurityManager 引导有关， **业务逻辑零侵入**。

---

## 📝 业务代码（AuthService.java）

```java
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

- 本类 **与其他 6 个示例完全一致**（证明切换框架业务代码零改动）
- 仅导入语句依赖 ddd4j-core（不依赖具体的 sa-token/shiro/security API）

---

## 🔄 切换鉴权框架

| 切换目标          | pom.xml 变化                                           |
|-------------------|--------------------------------------------------------|
| Sa-Token → Shiro  | 替换 `ddd4j-auth-{sa-token,shiro}` + 添加 `shiro-core` |
| Shiro → Security  | 替换 `ddd4j-auth-{shiro,security}`                     |
| Spring → Quarkus  | `ddd4j-runtime-{spring,quarkus}`                       |
| Quarkus → Javalin | `ddd4j-runtime-{quarkus,guice}`                        |

业务代码（`AuthController`、`AuthService`、`AuthModule` 中的数据源部分） **无需任何修改**。

---

## 🏗️ 架构图

```
┌─────────────────────────────────────────┐
│ 业务代码（AuthController/AuthService）  │ ← 本示例：业务代码完全一致
├─────────────────────────────────────────┤
│ ddd4j SubjectKit 统一鉴权入口            │ ← 框架无关
├─────────────────────────────────────────┤
│ ddd4j-auth-{sa-token|shiro|security}    │ ← 鉴权适配层
├─────────────────────────────────────────┤
│ ddd4j-runtime-{spring|quarkus|guice}    │ ← 框架桥接层
├─────────────────────────────────────────┤
│ Sa-Token / Shiro / Spring Security      │ ← 具体鉴权框架
└─────────────────────────────────────────┘
```

---

## 📁 项目结构

```
ddd4j-sample-javalin-shiro/
├── pom.xml                              # 依赖配置（ddd4j-auth-shiro + ddd4j-runtime-guice + shiro-core）
├── README.md                            # 本文档
└── src/
    ├── main/java/io/ddd4j/sample/javalin/shiro/
    │   ├── JavalinShiroApplication.java    # Javalin 启动 main（端口 7000）
    │   ├── config/
    │   │   ├── AuthModule.java             # Guice Module（注册 Auth + Order + Goods）
    │   │   └── AuthConfig.java             # Shiro SecurityManager 引导（Shiro 特有）
    │   ├── controller/
    │   │   └── AuthController.java         # 业务代码（与其他示例一致）
    │   ├── service/
    │   │   └── AuthService.java            # 业务代码（与其他示例完全一致）
    │   ├── order/                          # 订单域（充血模型 + 5 个事件）
    │   │   ├── application/{OrderApplicationService, CreateOrderCommand, AddOrderLineCommand}.java
    │   │   ├── domain/event/{5 个}.java
    │   │   ├── domain/model/{Order, OrderLine, OrderStatus, Money}.java
    │   │   ├── domain/repository/OrderRepository.java
    │   │   ├── domain/service/OrderDomainService.java
    │   │   ├── infrastructure/InMemoryOrderRepository.java
    │   │   └── web/{OrderResource, dto/}.java
    │   └── goods/                        # 商品域（Model/Query CRUD 模式）
    │       ├── application/GoodsApplicationService.java
    │       ├── domain/{Goods, GoodsId, GoodsStatus, GoodsRepository, GoodsQuery}.java
    │       ├── infrastructure/InMemoryGoodsRepository.java
    │       └── web/{GoodsResource, GoodsQueryResource, dto/}.java
    ├── main/resources/
    │   └── application.yml                 # 端口配置
    └── test/java/io/ddd4j/sample/javalin/shiro/
        └── AuthControllerTest.java         # 集成测试
```

### 🛒 业务能力（Order + Goods）

**Order（充血模型轨）**

- `POST /orders`                — 创建草稿订单
- `GET  /orders/{id}`           — 查询订单
- `GET  /orders/by-order-no?orderNo=xxx` — 按订单号查询
- `POST /orders/{id}/lines`     — 添加订单行
- `POST /orders/{id}/pay`       — 支付订单
- `POST /orders/{id}/ship`      — 发货订单
- `POST /orders/{id}/cancel`    — 取消订单
- `GET  /orders/{id}/discount`  — 预览折扣（领域服务）

**Goods（Model/Query 轨）**

- `POST /api/goods`                          — 创建商品
- `PUT  /api/goods/{id}`                     — 更新商品
- `PUT  /api/goods/{id}/status?status=ON_SALE` — 调整状态
- `DELETE /api/goods/{id}`                   — 软删除
- `GET  /api/goods/{id}`                     — 按 ID 查询
- `GET  /api/goods/by-code?code=SKU-001`    — 按编码查询
- `GET  /api/goods/page?current=1&size=10`  — 分页查询（充血）
- `GET  /api/goods/list?...`                — 列表查询（充血）
- `GET  /api/goods/count?...`               — 计数（充血）

**curl 示例**

```bash
# 1) 创建商品
curl -X POST 'http://localhost:7000/api/goods' \
  -H 'Content-Type: application/json' \
  -d '{"code":"SKU-001","name":"iPhone 15","price":5999.00,"stock":100}'

# 2) 创建草稿订单
curl -X POST 'http://localhost:7000/orders' \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"O-2026-0001","buyerId":"10001","buyerName":"Alice"}'

# 3) 添加订单行
curl -X POST 'http://localhost:7000/orders/{id}/lines' \
  -H 'Content-Type: application/json' \
  -d '{"goodsId":"1001","goodsName":"iPhone 15","quantity":1,"unitPrice":5999.00}'

# 4) 支付 → 发货 → 取消
curl -X POST 'http://localhost:7000/orders/{id}/pay'
curl -X POST 'http://localhost:7000/orders/{id}/ship'
curl -X POST 'http://localhost:7000/orders/{id}/cancel'

# 5) 折扣预览
curl 'http://localhost:7000/orders/{id}/discount'

# 6) 商品分页查询
curl 'http://localhost:7000/api/goods/page?current=1&size=10&status=ON_SALE&orderBys=price_DESC'
```

> 业务代码（Order/Goods 域、Application、Infrastructure、Web）与另外 3 个鉴权示例
> （quarkus-satoken、quarkus-shiro、javalin-satoken） **完全一致**，仅包名不同。

---

## 🔗 相关示例

| 框架                | Sa-Token                         | Shiro                                  | Security                          |
|---------------------|----------------------------------|----------------------------------------|-----------------------------------|
| Spring Boot（外部） | `ddd4j-boot-sample-auth-satoken` | `ddd4j-boot-sample-auth-shiro`         | `ddd4j-boot-sample-auth-security` |
| Quarkus             | `ddd4j-sample-quarkus-satoken`   | `ddd4j-sample-quarkus-shiro`           | （待补充）                        |
| Javalin             | `ddd4j-sample-javalin-satoken`   | `ddd4j-sample-javalin-shiro`（本示例） | （待补充）                        |

---

## 📄 相关文档

- [ddd4j 主项目](https://github.com/redacted-legacy-family/ddd4j)
- [ddd4j-auth 模块文档](https://github.com/ddd-4-java/ddd4j/tree/feature/2.0.x/ddd4j-auth)
- [SubjectKit API 文档](https://github.com/ddd-4-java/ddd4j/blob/feature/2.0.x/ddd4j-core/src/main/java/io/ddd4j/core/util/SubjectKit.java)
- [Apache Shiro 官方文档](https://shiro.apache.org/)

</details>

</details>
