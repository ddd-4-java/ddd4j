# Vert.x Shiro

入口 `io.ddd4j.sample.vertx.shiro.VertxShiroApplication`，默认端口 8087。复用共享 Order 业务和现有 ddd4j-shiro Subject
适配器；原生会话与订单均为单实例内存演示，不含 RBAC 平台或持久化会话。

- `POST /auth/login`，JSON `{"username":"alice","password":"sample-password"}`；返回原生 token。演示账号 `alice` 有
  `order:read/order:write`；`reader` 只有 `order:read`，同一演示密码。
- `Authorization: Bearer TOKEN` 访问 `POST /orders`、`GET /orders/:id`、`POST /orders/:id/lines`。
- 认证失败 401；写权限不足 403 且在业务写入前拒绝；`POST /auth/logout` 失效当前原生会话。
- `/health` 在认证 worker 队列之前响应，用于判断事件循环是否被阻塞。

每次业务任务进入现有 WebContextScope 和原生 Shiro worker 作用域，绑定真实 Subject；成功、业务异常和断连后的完成都恢复原线程状态。native/DDD
权限结果一致。`X-Request-Id` 仅用于关联正常请求观测，不新增诊断 HTTP 端点。测试通过包内只读完成观察器关联 held/disconnected
请求；观察器不清理任何全局或线程状态。

每个请求在 Vert.x 工作线程上绑定样例自己的 Shiro 管理器和主体，业务结束后恢复该线程先前的绑定。调用方的进程级管理器在样例运行期间继续生效。

断连不会取消已准入的同步业务；它仍可能提交。close 停止接入并等待业务完成后，按 owner 恢复前任提供器并释放本实例 Vert.x。使用同一
JVM 的原生全局认证配置仅支持单个活动样例；后任替换不能被旧实例关闭覆盖。不承诺任意用户异步线程自动传播 native context。

使用当前发行线要求的 JDK 和仓库 `./mvnw`。四场景的 required 检查为：

```sh
./mvnw -pl ddd4j-samples/ddd4j-sample-vertx,ddd4j-samples/ddd4j-sample-vertx-cqrs,ddd4j-samples/ddd4j-sample-vertx-satoken,ddd4j-samples/ddd4j-sample-vertx-shiro -am verify
```

启动使用模块 `target/classes` 与其 Maven runtime 依赖类路径；不得加入 `target/test-classes`。`sample.port` 可覆盖默认端口，绑定
`127.0.0.1`。`main` 与测试调用的 `start` 共用业务路由；关闭 hook 等待自身资源退出。详细当前构件验证和独立审查状态见 S06
任务报告。
