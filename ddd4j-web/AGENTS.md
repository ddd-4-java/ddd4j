# ddd4j-web

先区分请求本身的语义与服务器的回调时机。Bearer、公共错误和幂等契约进入 web-core；共享断言进入 web-testkit。只有原生框架知道提前响应、连接重置及异常映射发生在哪个回调。

## 按变更选择模块

| 工件 | 修改范围 |
|---|---|
| [ddd4j-web-core](ddd4j-web-core/AGENTS.md) | HTTP 请求上下文、身份策略、幂等和异常翻译 |
| [ddd4j-web-testkit](ddd4j-web-testkit/AGENTS.md) | 跨服务器的 HTTP 测试契约与客户端夹具 |
| [ddd4j-web-javalin](ddd4j-web-javalin/AGENTS.md) | Javalin 处理器、失败事件及请求作用域 |
| [ddd4j-web-quarkus](ddd4j-web-quarkus/AGENTS.md) | CDI 发现的 JAX-RS 请求过滤器与异常处理 |
| [ddd4j-web-micronaut](ddd4j-web-micronaut/AGENTS.md) | Micronaut HTTP 上下文、地址解析与错误响应 |
| [ddd4j-web-vertx](ddd4j-web-vertx/AGENTS.md) | Vert.x Router 请求上下文、提前响应与租约释放 |
| [ddd4j-web-helidon](ddd4j-web-helidon/AGENTS.md) | Helidon MP 的 JAX-RS 过滤器与异常映射 |
| [ddd4j-web-dropwizard](ddd4j-web-dropwizard/AGENTS.md) | Dropwizard Bundle 向 Jersey 注册的请求和响应组件 |
| [ddd4j-web-webflux](ddd4j-web-webflux/AGENTS.md) | WebFlux 过滤器、exchange 属性与 Reactor Context |
| [ddd4j-web-webmvc](ddd4j-web-webmvc/AGENTS.md) | Spring MVC 拦截器、请求版本与完成回调 |

## 验证范围

从仓库根目录执行整组验证时使用下列命令。只改一个实现时，进入该模块指引选择对应的行为测试。

```sh
./mvnw -pl ddd4j-web -am verify
```
