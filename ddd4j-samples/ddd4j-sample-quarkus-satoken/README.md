# ddd4j-sample-quarkus-satoken

## 当前商品交易入口

此模块以 Sa-Token 方式运行共享商品交易。先按[示例总览](../README.md#启动默认订单示例)配置 MySQL。以下命令从仓库根目录运行，使用实际
Maven 版本替换 `<version>`。

```bash
module=ddd4j-samples/ddd4j-sample-quarkus-satoken
./mvnw -pl "$module" -am -DskipTests -Dmaven.javadoc.skip=true package
java -jar "$module/target/quarkus-app/quarkus-run.jar"
```

业务请求携带 `X-Tenant-Id`。商品地址为 `/orders/catalog/goods/{spuCode}`，订单地址为 `/orders/catalog/orders/{orderNo}`
。支付、取消和履约分别使用订单子路径 `/payment`、`/cancel`、`/fulfill`。支付请求必须提供非空 `Idempotency-Key`，重试时复用原键和实付金额。

本地演示登录使用 `POST /auth/login`，请求体为 `{"username":"admin","password":"123456"}`。将令牌放入
`Authorization: Bearer <token>` 请求头。

<details>
<summary>商品交易迁移前的说明</summary>

本示例在 Quarkus 中组合 ddd4j 的订单、商品和 RBAC 场景，鉴权实现为 Sa-Token。三条 ddd4j 线使用各自的 JDK、Quarkus
与第三方依赖；本页描述三线共同的 HTTP 和业务行为。实际依赖版本以本线 pom.xml 和根 BOM 为准。

## 运行与登录

从仓库根目录，使用本线 JDK 运行 HTTP 和 RBAC 测试。

~~~bash
./mvnw -pl ddd4j-samples/ddd4j-sample-quarkus-satoken -am test
~~~

构建并启动本地示例：

~~~bash
./mvnw -pl ddd4j-samples/ddd4j-sample-quarkus-satoken -am package
java -jar ddd4j-samples/ddd4j-sample-quarkus-satoken/target/quarkus-app/quarkus-run.jar
~~~

应用配置端口为 8083。应用启动后，可使用 RbacConfig 创建的样例账号 admin/123456 登录；POST /auth/login 接受 JSON
用户名和密码，响应顶层含有 `token`。

~~~bash
curl -s -X POST http://localhost:8083/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}'
~~~

后续访问 GET /auth/me、GET /auth/check/permission?permission=user:list 等受保护接口时，在 Authorization 头传入 Bearer
TOKEN（将 TOKEN 替换为登录响应中的 token）。GET /auth/status 可检查登录状态。用户名、密码、端口和请求格式均对应当前源码与 HTTP
测试。

## 实现边界

- 鉴权资源：src/main/java/io/ddd4j/sample/quarkus/satoken/rbac/AuthenticationResource.java；提供登录、登出、当前用户与权限/角色查询。
- RBAC 业务：src/main/java/io/ddd4j/sample/quarkus/satoken/rbac/RbacService.java；校验用户与密码，通过 SubjectKit 签发
  token 并读取当前身份。
- 样例数据：src/main/java/io/ddd4j/sample/quarkus/satoken/rbac/RbacConfig.java；启动时初始化用户、角色和权限。
- Maven 依赖：ddd4j-auth-satoken 与 ddd4j-runtime-quarkus；Quarkus 1.x/3.x REST、CDI 和 JAX-RS 包名按本线 POM 与源码适配。

Sa-Token 资源以鉴权注解保护接口，业务层通过 SubjectKit 访问当前 Subject。 POST /auth/orders/{id}/pay 演示 order:pay
权限，DELETE /auth/users/{id} 演示 admin 角色与 user:delete 权限；这些端点是鉴权演示，订单领域的业务写入使用下方 /orders
资源。

### 🛒 业务能力（Order + Goods）

本示例在 Sa-Token 鉴权基础上植入了完整的 DDD 双轨业务：

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

将登录响应中的令牌赋给 `TOKEN`，用创建订单返回的 `data.id` 替换 `{id}`。

```bash
# 1) 创建商品
curl -X POST 'http://localhost:8083/api/goods' \
  -H 'Content-Type: application/json' \
  -d '{"code":"SKU-001","name":"iPhone 15","price":5999.00,"stock":100}'

# 2) 创建草稿订单
curl -X POST 'http://localhost:8083/orders' \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"O-2026-0001","buyerId":"10001","buyerName":"Alice"}'

# 3) 添加订单行
curl -X POST 'http://localhost:8083/orders/{id}/lines' \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"goodsId":"1001","goodsName":"iPhone 15","quantity":1,"unitPrice":5999.00}'

# 4) 支付 → 发货；取消使用另一笔未发货订单
curl -X POST 'http://localhost:8083/orders/{id}/pay' \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json'
curl -X POST 'http://localhost:8083/orders/{id}/ship' \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json'
# 另一笔未发货订单可调用 POST /orders/{id}/cancel

# 5) 折扣预览
curl 'http://localhost:8083/orders/{id}/discount' -H "Authorization: Bearer $TOKEN"

# 6) 商品分页查询
curl 'http://localhost:8083/api/goods/page?current=1&size=10&status=ON_SALE&orderBys=price_DESC'
```

> 业务代码（Order/Goods 域、Application、Infrastructure、Web）与另外 3 个鉴权示例
> （Quarkus/Javalin 的 Sa-Token/Shiro 版本）保持相同业务场景；框架和鉴权实现分别适配。

### 🔗 相关示例

- 同一框架的其他认证方案：`ddd4j-sample-quarkus-shiro`
- 其他框架的同认证方案：外部 `ddd4j-boot-sample-auth-satoken`、`ddd4j-sample-javalin-satoken`
- 核心 DDD 示例：`ddd4j-sample-quarkus`

### 📄 相关文档

- [ddd4j 主项目](https://github.com/ddd-4-java/ddd4j)
- [ddd4j-auth 模块](../../ddd4j-auth/pom.xml)
- [SubjectKit 源码](../../ddd4j-core/src/main/java/io/ddd4j/core/util/SubjectKit.java)

## S03 原生共享订单入口

原 `/orders`、Goods、RBAC 路由和 JSON DTO 保留。实际 CDI 入口复用 `ddd4j-sample-order-application` 与
`ddd4j-sample-order-local`，订单内存存储仅供演示，不证明持久化；旧直接构造器保留供既有消费者/测试使用。查询仍返回原完整明细和币种，折扣规则沿用原领域服务。

所有订单请求需要原生登录；写操作需要 `order:pay`，取消需要 `order:cancel`。权限基于原生已匹配资源方法，拒绝发生在业务写入前。
`admin/123456` 有写/取消权限，`user/123456` 默认只读，演示账户仅用于本地样例。实际登录、角色/权限、退出令牌失效均由现有
native 引擎处理。

Quarkus 1 使用 RESTEasy Classic 的请求完成回调，Quarkus 3 使用 Quarkus REST 的请求完成回调。响应过滤器和完成回调按请求所有权释放自身的
Sa-Token 上下文，并恢复请求前的上下文。各版本的同步 HTTP、鉴权拒绝、退出失效、断连及 JVM
启停验证见 [R14 实施计划](../../docs/superpowers/plans/2026-10-10-three-line-product-convergence.md#r14三线存储和多平台完整验收)。

</details>
