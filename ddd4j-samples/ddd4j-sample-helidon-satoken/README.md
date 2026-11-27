# Helidon SaToken 样例

使用当前发行线要求的 JDK、原生 Helidon MP/CDI/JAX-RS 和共享 Order domain/application。主入口调用 `start(int)`。HTTP 测试启动真实
Helidon Server，在生产 JAX-RS 应用上增加请求观测器，验证响应和断连后的资源清理。`server.port` 默认为8080；停止时配对
`Server.stop()`。

入口：`io.ddd4j.sample.helidon.satoken.HelidonSaTokenApplication`。

构建：仓库根执行 `./mvnw -pl ddd4j-samples/ddd4j-sample-helidon-satoken -am verify`，使用当前分支 JDK。将该模块 JAR 与
Maven runtime 依赖放入 classpath 后运行上述 main，不能使用 test classpath 作为产品运行依赖。

真实 SaToken 引擎驱动 `/auth/login`（username/password）、GET `/auth/me`、GET `/auth/resource?permission=...`、POST
`/auth/logout`。演示凭证：alice、reader，密码均为 sample-password；alice 有 order:read/order:write，reader 只有 order:
read。只供本地样例，未实现生产账号库。

共享 Order 端点沿用 `/api/orders`、`/{id}/lines`、`/{id}/pay` 与过滤查询，写操作要求 order:write。除登录外都要求
Authorization: Bearer；错误凭证401、缺失/失效令牌401、权限不足403。支付必须
Idempotency-Key。业务空订单支付失败不会伪造成认证成功。默认会话与订单均在本地内存中，重启后清空。

同步 JAX-RS 请求在优先级900创建 native scope，ddd4j web filter在1000打开 Web scope，响应先关闭 Web scope再清 native；Jersey
FINISHED 提供 native清理保底。只覆盖当前同步路径及连接断开后同步处理结束，不宣称任意异步挂起/跨线程传播。观测测试只读服务端真实
enter/exit/FINISHED 状态，不提供 debug HTTP 端点。应用关闭时按归属恢复 Subject SPI。

样例借用调用方已注册的 Sa-Token DAO，由调用方管理其生命周期。未注册时，样例创建 DAO 并在关闭时释放。全局配置按单活动样例使用。借用
DAO 时，样例须独占令牌名 `satoken` 与登录域 `login` 对应的会话命名空间；宿主使用相同组合时，需要独立的数据空间。借用存储中的会话按登出与过期策略清理。
