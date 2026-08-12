# Sa-Token 认证授权接入设计

- **日期**：2026-07-02
- **作者**：ddd4j 架构团队
- **状态**：已实施

## 1. 目标与范围

定义 ddd4j-auth-satoken 的能力概览、关键类路径、快速接入方式和最佳实践。

## 2. 能力概览

- **集成组件**：`ddd4j-auth/ddd4j-auth-satoken`
- **Spring 桥接**：`ddd4j-auth/ddd4j-auth-spring` 通过 `SubjectRegistrar` 将 `SubjectProvider` 注册到 `SubjectKit`
- **扩展工具**：`StpKit` 提供扩展载荷读取与类型转换、`SaTempKit` 提供临时授权码能力

## 3. 关键类与路径

| 类 | 路径 |
|---|------|
| Sa-Token Subject 实现 | `ddd4j-auth/ddd4j-auth-satoken/src/main/java/io/ddd4j/auth/satoken/subject/SaTokenSubject.java` |
| SubjectProvider 实现 | `ddd4j-auth/ddd4j-auth-satoken/src/main/java/io/ddd4j/auth/satoken/subject/SaTokenSubjectProvider.java` |
| 扩展常量 | `ddd4j-auth/ddd4j-auth-satoken/src/main/java/io/ddd4j/auth/satoken/SaConstants.java` |
| 载荷读取 | `ddd4j-auth/ddd4j-auth-satoken/src/main/java/io/ddd4j/auth/satoken/util/StpKit.java` |
| 临时 Token | `ddd4j-auth/ddd4j-auth-satoken/src/main/java/io/ddd4j/auth/satoken/util/SaTempKit.java` |

## 4. 快速接入

1. 引入依赖（外部项目建议通过 BOM）：`ddd4j-bom` 中声明了 `ddd4j-auth-satoken` 版本
2. 配置 Sa-Token 基本属性（如 Token 风格、过期时间、是否 JWT 等）
3. 在登录成功后，写入必要的扩展载荷（如用户、机构、角色等）

## 5. 载荷读取示例

```java
String userId = StpKit.getUserIdAsString();
Long orgId = StpKit.getOrgIdAsLong();
String roleId = StpKit.getRoleIdAsString();
```

## 6. 临时授权码（一次性 Token）

- 校验逻辑：`SaTempKit.java`
- 典型使用：签发临时码用于敏感操作确认，后端校验通过后立即失效

## 7. 最佳实践

- 所有鉴权受保护的接口统一走 Sa-Token 拦截
- 在业务层优先通过 `SubjectKit` 读取认证主体和权限语义，必要时再使用 `StpUtil`/`StpKit`
- 对 JWT 载荷的扩展键使用 `SaConstants` 约定字段，避免语义不一致
- 对管理端/移动端区分 `deviceType`，用于风控与日志聚合
