# ddd4j-auth-satoken

`ddd4j-auth-satoken` 是纯 Java 的 Sa-Token `Subject` 适配，不提供 Spring Boot 自动装配，也不提供 OAuth 授权服务器。

## 生产启动配置

应用必须在开始接收请求前安装一次安全配置。`tokenNamespace` 同时决定 token 名称和 Sa-Token 分布式 key 前缀；JWT Simple 使用服务端 token 映射，因此支持登出、踢人和 TTL 到期后的立即失效。

```java
SaTokenSecurityProperties security = new SaTokenSecurityProperties();
security.setTokenNamespace("order-service");
security.setLoginType("user");
security.setTokenTtlSeconds(7_200L);
security.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
security.setJwtSecretKey(System.getenv("DDD4J_SATOKEN_JWT_SECRET"));
security.setJwtIssuer("order-service");
security.setJwtAudience("order-clients");
SaTokenSecurityConfigurer.configure(security);
```

JWT Simple 的密钥至少为 32 个 UTF-8 字节，`issuer` 与 `audience` 必填。`Subject.verify(token)` 会校验 JWT 签名、账号体系、issuer、audience 和服务端 token 映射；任一校验失败或认证基础设施异常均返回未认证结果，不会回退到线程主体。

权限和角色不缓存在 JWT 中，始终经 `SubjectDataProvider` 查询。因此业务侧权限变化会在下一次鉴权时生效；若数据源自身带缓存，应由业务配置相应失效策略。

无状态 JWT 不属于该模块的生产支持范围，因为它不能同时满足 `AuthPrincipal` 恢复和服务端撤销语义。
