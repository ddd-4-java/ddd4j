# Sa-Token 认证授权接入指南

## 能力概览
- 集成组件：`ddd4j-cmpt/ddd4j-cmpt-satoken`
- 自动配置注册：`META-INF/spring.factories` → `SaTokenEnhanceAutoConfiguration`
- 扩展工具：`StpKit` 提供扩展载荷读取与类型转换、`SaTempKit` 提供临时授权码能力

## 关键类与路径
- 自动配置：`ddd4j-cmpt/ddd4j-cmpt-satoken/src/main/resources/META-INF/spring.factories:3`
- 扩展常量：`ddd4j-cmpt/ddd4j-cmpt-satoken/src/main/java/io/ddd4j/boot/cmpt/satoken/SaConstants.java`
- 载荷读取：`ddd4j-cmpt/ddd4j-cmpt-satoken/src/main/java/io/ddd4j/boot/cmpt/satoken/util/StpKit.java:173-183`
- 类型转换：`ddd4j-cmpt/ddd4j-cmpt-satoken/src/main/java/io/ddd4j/boot/cmpt/satoken/util/Functions.java`

## 快速接入
1. 引入依赖（外部项目建议通过 BOM）
   - `ddd4j-bom` 中声明了 `ddd4j-cmpt-satoken` 版本，直接添加依赖即可
2. 配置 Sa-Token 基本属性（如 Token 风格、过期时间、是否 JWT 等）
3. 在登录成功后，写入必要的扩展载荷（如用户、机构、角色等），便于后续读取

## 载荷读取示例
```java
String userId = StpKit.getUserIdAsString();
Long orgId = StpKit.getOrgIdAsLong();
String roleId = StpKit.getRoleIdAsString();
```

## 临时授权码（一次性 Token）
- 校验逻辑：`ddd4j-cmpt/ddd4j-cmpt-satoken/src/main/java/io/ddd4j/boot/cmpt/satoken/util/SaTempKit.java:99-109`
- 典型使用：签发临时码用于敏感操作确认，后端校验通过后立即失效

## 统一错误码
- 认证/授权相关错误码集中在：`ddd4j-core/src/main/java/io/ddd4j/boot/core/ApiCodeValue.java:56-110`
- 建议在异常处理处统一映射为 `ApiRestResponse` 输出，确保客户端行为一致

## 最佳实践
- 所有鉴权受保护的接口统一走 Sa-Token 拦截；在业务层通过 `StpUtil`/`StpKit` 读取上下文信息
- 对 JWT 载荷的扩展键使用 `SaConstants` 约定字段，避免语义不一致
- 对管理端/移动端区分 `deviceType`，用于风控与日志聚合

