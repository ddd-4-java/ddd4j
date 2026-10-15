# ddd4j-sample-quarkus-shiro

本示例在 Quarkus 中组合 ddd4j 的订单、商品和 RBAC 场景，鉴权实现为 Shiro。三条 ddd4j 线使用各自的 JDK、Quarkus 与第三方依赖；本页描述三线共同的 HTTP 和业务行为。实际依赖版本以本线 pom.xml 和根 BOM 为准。

## 运行与登录

从仓库根目录，使用该分支对应的 JDK 运行 HTTP/RBAC 测试：

~~~bash
./mvnw -pl ddd4j-samples/ddd4j-sample-quarkus-shiro -am test
~~~

应用配置端口为 8084。应用启动后，可使用 RbacConfig 创建的样例账号 admin/123456 登录；POST /auth/login 接受 JSON 用户名和密码，响应顶层包含 token：

~~~bash
curl -s -X POST http://localhost:8084/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}'
~~~

后续访问 GET /auth/me、GET /auth/check/permission?permission=user:list 等受保护接口时，在 Authorization 头传入 Bearer TOKEN（将 TOKEN 替换为登录响应中的 token）。GET /auth/status 可检查登录状态。用户名、密码、端口和请求格式均对应当前源码与 HTTP 测试。

## 实现边界

- 鉴权资源：src/main/java/io/ddd4j/sample/quarkus/shiro/rbac/AuthenticationResource.java；提供登录、登出、当前用户与权限/角色查询。
- RBAC 业务：src/main/java/io/ddd4j/sample/quarkus/shiro/rbac/RbacService.java；校验用户与密码，通过 SubjectKit 签发 token 并读取当前身份。
- 样例数据：src/main/java/io/ddd4j/sample/quarkus/shiro/rbac/RbacConfig.java；启动时初始化用户、角色和权限。
- Maven 依赖：ddd4j-auth-shiro 与 ddd4j-runtime-quarkus；Quarkus 1.x/3.x REST、CDI 和 JAX-RS 包名按本线 POM 与源码适配。

Shiro 资源显式调用 RbacService 的登录、角色和权限校验；两种实现维持相同的 HTTP 契约。 POST /auth/orders/{id}/pay 演示 order:pay 权限，DELETE /auth/users/{id} 演示 admin 角色与 user:delete 权限；这些端点是鉴权演示，订单领域的业务写入使用下方 /orders 资源。

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
curl -X POST 'http://localhost:8084/api/goods' \
  -H 'Content-Type: application/json' \
  -d '{"code":"SKU-001","name":"iPhone 15","price":5999.00,"stock":100}'

# 2) 创建草稿订单
curl -X POST 'http://localhost:8084/orders' \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"O-2026-0001","buyerId":"10001","buyerName":"Alice"}'

# 3) 添加订单行
curl -X POST 'http://localhost:8084/orders/{id}/lines' \
  -H 'Content-Type: application/json' \
  -d '{"goodsId":"1001","goodsName":"iPhone 15","quantity":1,"unitPrice":5999.00}'

# 4) 支付 → 发货；取消使用另一笔未发货订单
curl -X POST 'http://localhost:8084/orders/{id}/pay'
curl -X POST 'http://localhost:8084/orders/{id}/ship'
# 另一笔未发货订单可调用 POST /orders/{id}/cancel

# 5) 折扣预览
curl 'http://localhost:8084/orders/{id}/discount'

# 6) 商品分页查询
curl 'http://localhost:8084/api/goods/page?current=1&size=10&status=ON_SALE&orderBys=price_DESC'
```

> 业务代码（Order/Goods 域、Application、Infrastructure、Web）与另外 3 个鉴权示例
> （Quarkus/Javalin 的 Sa-Token/Shiro 版本）保持相同业务场景；框架和鉴权实现分别适配。

### 🔗 相关示例

- 同一框架的其他认证方案：`ddd4j-sample-quarkus-satoken`
- 其他框架的同认证方案：外部 `ddd4j-boot-sample-auth-shiro`、`ddd4j-sample-javalin-shiro`
- 核心 DDD 示例：`ddd4j-sample-quarkus`

### 📄 相关文档

- [ddd4j 主项目](https://github.com/ddd-4-java/ddd4j)
- [ddd4j-auth 模块](../../ddd4j-auth/pom.xml)
- [SubjectKit 源码](../../ddd4j-core/src/main/java/io/ddd4j/core/util/SubjectKit.java)
