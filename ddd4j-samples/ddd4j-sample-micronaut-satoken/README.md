# Micronaut Sa-Token 订单样例

## 当前商品交易入口

此模块以 Sa-Token 方式运行共享商品交易。先按[示例总览](../README.md#启动默认订单示例)配置 MySQL。以下命令从仓库根目录运行，使用实际
Maven 版本替换 `<version>`。

```bash
module=ddd4j-samples/ddd4j-sample-micronaut-satoken
./mvnw -pl "$module" -am -DskipTests -Dmaven.javadoc.skip=true -DincludeScope=runtime -Dmdep.outputFile=target/runtime-classpath.txt package dependency:build-classpath
java -cp "$module/target/ddd4j-sample-micronaut-satoken-<version>.jar:$(cat "$module/target/runtime-classpath.txt")" io.ddd4j.sample.micronaut.satoken.MicronautSaTokenApplication
```

业务请求携带 `X-Tenant-Id`。商品地址为 `/orders/catalog/goods/{spuCode}`，订单地址为 `/orders/catalog/orders/{orderNo}`
。支付、取消和履约分别使用订单子路径 `/payment`、`/cancel`、`/fulfill`。

本地演示登录使用 `POST /auth/login`，请求体为 `{"username":"alice","password":"sample-password"}`。将令牌放入
`Authorization: Bearer <token>` 请求头。

<details>
<summary>商品交易迁移前的说明</summary>

在仓库根目录使用当前分支对应的 JDK 和 Maven Wrapper。样例监听 `127.0.0.1`，默认 HTTP 端口为 `8085`。

| 发布线  | JDK | Micronaut HTTP 运行时 |
|---------|-----|-----------------------|
| `1.0.x` | 8   | 3.10.10               |
| `2.0.x` | 17  | 4.10.26               |
| `3.0.x` | 21  | 4.10.26               |

将启动命令中的 `<version>` 换成该模块 `target` 中实际生成的 JAR 版本。

```bash
module=ddd4j-samples/ddd4j-sample-micronaut-satoken
./mvnw -pl "$module" -am -Dmaven.test.skip=true -Dmaven.javadoc.skip=true \
  -DincludeScope=runtime -Dmdep.outputFile=target/runtime-classpath.txt \
  package dependency:build-classpath
java -Dmicronaut.server.host=127.0.0.1 -cp "$module/target/ddd4j-sample-micronaut-satoken-<version>.jar:$(cat "$module/target/runtime-classpath.txt")" \
  io.ddd4j.sample.micronaut.satoken.MicronautSaTokenApplication
```

## 订单和认证

本地演示账号 `alice / sample-password` 可以读写订单，`reader / sample-password` 只读。取消订单要求 `order:cancel`
，两个演示账号均无此权限。固定凭证、内存订单和会话仅用于本地演示。

- `POST /auth/login` 接收 JSON 字段 `username` 和 `password`，返回 `token`。后续请求携带 `Authorization: Bearer <token>`。
- `GET /auth/me` 查询当前会话身份，`POST /auth/logout` 使原令牌失效。
- `POST /orders` 创建订单，`POST /orders/lines` 增加明细，`POST /orders/{id}/payment?key=...` 支付订单。三种写入均调用共享订单应用服务。
- `GET /orders/{id}` 按 ID 查询，`POST /orders/query` 分页查询。JSON 中必须传入从 1 开始的 `page` 和范围为 1 至 100 的
  `size`，可按 `buyerId` 和 `status` 筛选。
- `POST /orders/{id}/cancel` 先检查原生取消权限，再调用共享订单应用服务。拒绝请求时，订单状态保持原值。

## 认证上下文

样例使用原生 Sa-Token 认证和权限判定，并通过 ddd4j `SubjectProvider` 暴露业务身份。`SaTokenSubjectDataBridge` 将原生
`StpInterface` 接入同一个 `SubjectDataProvider`。

- Micronaut 3 通过 `@NativeAuthenticated` 拦截器在同步 JSON 路由线程绑定原生认证上下文，路由执行结束后恢复该线程的原有状态。
- Micronaut 4 的认证过滤器通过 `ThreadPropagatedContextElement` 在参与请求的框架线程上保存和恢复原生上下文。阻塞式
  continuation 使用 `request(request).proceed()` 将更新后的请求和上下文传给后续处理。标准 Web 过滤器仍处理请求上下文和错误响应。

这里的线程上下文契约涵盖同步 JSON 路由。Micronaut 4 的生命周期测试也观察客户端断连后路由结束时的上下文恢复。

关闭 ApplicationContext 时释放样例注册的会话存储，按所有权恢复配置及权限提供器。调用方已注册的 Sa-Token DAO
继续由调用方管理。Sa-Token 全局配置按单活动样例使用。样例沿用 `satoken` 令牌名和 `login` 登录域。借用 DAO
时，该会话命名空间应由样例独占；宿主使用同一组合时，为样例配置独立的 DAO 数据空间。借用 DAO 中的会话继续按登出与过期策略管理。

</details>
