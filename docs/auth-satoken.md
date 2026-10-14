# Sa-Token 认证授权接入指南

`ddd4j-auth/ddd4j-auth-satoken` 提供纯 Java 的 Sa-Token `Subject` 适配。应用在开始接收请求前，通过 `SaTokenSecurityProperties` 和 `SaTokenSecurityConfigurer.configure(...)` 安装安全配置；该模块不提供 Spring Boot 自动配置或 OAuth 授权服务器。可运行的配置示例与 JWT 要求见 [模块 README](../ddd4j-auth/ddd4j-auth-satoken/README.md)。

## 请求与授权链路

1. WebMVC 和 WebFlux 的 `BearerSubjectAuthenticator` 从请求读取 Bearer token，经 `SubjectProvider` 验证主体；受保护路径缺失或无效 token 时返回未认证结果。公开路径由 `ddd4j.web.public-paths` 配置。
2. `SaTokenSubjectProvider` 提供 Sa-Token 的主体实现。`Subject.verify(token)` 校验 token、账号体系和服务端映射；JWT Simple 还校验签名、issuer 与 audience。权限和角色由业务提供的 `SubjectDataProvider` 查询。
3. 业务端按需使用 `Subject`/`StpKit` 获取当前主体和扩展信息。授权数据源如有缓存，由业务侧确定失效策略。

JWT Simple 密钥至少 32 个 UTF-8 字节，issuer、audience 必填。服务端 token 映射使登出、踢人和 TTL 到期后的 token 立即失效。无状态 JWT 不在该模块的生产支持范围内。

## 三线适配边界

三线保持以上认证和撤销语义一致。JDK、Sa-Token、Spring 及其他三方依赖版本由各线 POM 和依赖清单分别确定；不要用一条线的依赖声明覆盖其他线。
