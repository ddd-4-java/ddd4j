# ddd4j-auth 鉴权框架迁移指南

> **适用版本**：ddd4j-auth 3.4.x+
> **迁移目标**：旧项目（Shiro / Spring Security）平滑迁移到 ddd4j-auth + sa-token
> **核心策略**：通过 `SubjectKit` 统一入口，过渡期多鉴权并存，最终统一 sa-token

---

## 一、迁移总览

### 1.1 为什么迁移到 sa-token

| 维度              | Shiro             | Spring Security      | sa-token（推荐）                     |
|-----------------|-------------------|----------------------|----------------------------------|
| **学习成本**        | 高（INI/Realm 体系复杂） | 极高（FilterChain 配置繁琐） | 低（`StpUtil.login()` 一行搞定）        |
| **多账号体系**       | 多 Realm 配置复杂      | 多 SecurityContext    | `StpLogic("admin")` 天然支持         |
| **Token 管理**    | 需自行实现             | 需 OAuth2/JWT 扩展      | 内置 JWT/UUID/临时Token              |
| **Sa-Token 生态** | ❌                 | ❌                    | OAuth2 / SSO / API Key / 踢人 / 封禁 |
| **ddd4j 适配**    | ✅ 兼容旧项目           | ✅ 兼容旧项目              | ✅ **主推方案**                       |

### 1.2 迁移四阶段路径

```
阶段1：接入 ddd4j-core（低风险，可回滚）
   ↓
阶段2：双写运行（Shiro/Security 与 SubjectKit 并存）
   ↓
阶段3：灰度切换（试点服务引入 sa-token）
   ↓
阶段4：全量切换（移除旧鉴权依赖，统一 sa-token）
```

---

## 二、Shiro → sa-token 迁移

### 2.1 阶段 1：接入 ddd4j-core（不改现有 Shiro 代码）

**目标**：引入 ddd4j-auth 体系，业务代码改为调 `SubjectKit`，但底层仍是 Shiro。

#### 步骤 1：添加依赖

```xml
<!-- 保留原有 Shiro 依赖 -->
<dependency>
    <groupId>org.apache.shiro</groupId>
    <artifactId>shiro-spring-boot-starter</artifactId>
</dependency>

<!-- 新增 ddd4j-core（纯 Java，零 Spring） -->
<dependency>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-core</artifactId>
    <version>${ddd4j.version}</version>
</dependency>

<!-- 新增 ddd4j-auth-shiro（Shiro 适配，零 Spring） -->
<dependency>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-auth-shiro</artifactId>
    <version>${ddd4j.version}</version>
</dependency>

<!-- 新增 ddd4j-auth-spring（Spring 桥接，自动注册 SubjectProvider） -->
<dependency>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-auth-spring</artifactId>
    <version>${ddd4j.version}</version>
</dependency>
```

#### 步骤 2：注册 SubjectProvider（自动完成）

`ddd4j-auth-spring` 的 `SubjectRegistrar` 会自动检测 classpath 中的 Shiro，注册 `ShiroSubjectProvider`。**无需手动配置**。

#### 步骤 3：注册权限数据源

```java
@Configuration
public class AuthConfig {

    @Bean
    public SubjectDataProvider subjectDataProvider(PermissionService permissionService,
                                                    RoleService roleService) {
        return new SubjectDataProvider() {
            @Override
            public List<String> getPermissionList(AuthPrincipal principal) {
                return permissionService.findByUserId(principal.getUserId());
            }
            @Override
            public List<String> getRoleList(AuthPrincipal principal) {
                return roleService.findByUserId(principal.getUserId());
            }
        };
    }
}
```

#### 步骤 4：业务代码改造（Shiro API → SubjectKit）

```java
// ====== 改造前（直接调 Shiro API）======
Subject subject = SecurityUtils.getSubject();
subject.login(new UsernamePasswordToken(username, password));
if (!subject.hasRole("admin")) { ... }
Object principal = subject.getPrincipal();

// ====== 改造后（统一调 SubjectKit）======
String token = SubjectKit.login(AuthRequest.of(userId)
    .principal(principal)
    .timeout(7200));
if (!SubjectKit.hasRole("admin")) { ... }
AuthPrincipal principal = SubjectKit.getPrincipal();
```

**关键**：底层仍是 Shiro（`ShiroSubject` 委托 `SecurityUtils.getSubject()`），但业务代码已解耦。

### 2.2 阶段 2：双写运行

新旧鉴权 API 并存，逐步替换：

```java
// 旧代码保留（Shiro 原生 API 仍可用）
SecurityUtils.getSubject().hasRole("admin");

// 新代码使用 SubjectKit（推荐）
SubjectKit.hasRole("admin");
```

**回滚方式**：移除 `ddd4j-auth-*` 依赖即可，旧 Shiro 代码不受影响。

### 2.3 阶段 3：灰度切换到 sa-token

**试点服务**引入 sa-token，旧服务保留 Shiro：

```xml
<!-- 移除 Shiro 依赖 -->
<!-- <dependency><groupId>org.apache.shiro</groupId><artifactId>shiro-spring-boot-starter</artifactId></dependency> -->

<!-- 引入 sa-token -->
<dependency>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-auth-satoken</artifactId>
    <version>${ddd4j.version}</version>
</dependency>
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-spring-boot3-starter</artifactId>
</dependency>
```

`SubjectRegistrar` 会自动检测到 sa-token（优先级高于 Shiro），切换为 `SaTokenSubjectProvider`。

**业务代码零改动**——仍然调 `SubjectKit.hasRole("admin")`，但底层已从 Shiro 切换到 sa-token。

### 2.4 阶段 4：全量切换

所有服务移除 Shiro 依赖，统一 sa-token。此阶段需处理：

1. **Token 重发**：旧 Shiro Session Token 失效，用户需重新登录获取 sa-token Token
2. **权限缓存迁移**：Shiro CacheManager → sa-token Redis 持久化
3. **会话超时策略**：Shiro session.timeout → sa-token timeout 配置

### 2.5 Shiro → sa-token API 对照表

| Shiro API                                      | SubjectKit API（统一）              | sa-token 底层                             |
|------------------------------------------------|---------------------------------|-----------------------------------------|
| `SecurityUtils.getSubject().login(token)`      | `SubjectKit.login(AuthRequest)` | `StpUtil.login(id)`                     |
| `SecurityUtils.getSubject().logout()`          | `SubjectKit.logout()`           | `StpUtil.logout()`                      |
| `SecurityUtils.getSubject().getPrincipal()`    | `SubjectKit.getPrincipal()`     | `StpUtil.getSession().get("principal")` |
| `SecurityUtils.getSubject().hasRole(r)`        | `SubjectKit.hasRole(r)`         | `StpUtil.hasRole(r)`                    |
| `SecurityUtils.getSubject().isPermitted(p)`    | `SubjectKit.hasPermission(p)`   | `StpUtil.hasPermission(p)`              |
| `SecurityUtils.getSubject().isAuthenticated()` | `SubjectKit.isLogin()`          | `StpUtil.isLogin()`                     |
| `sessionDAO.delete(session)`                   | `SubjectKit.kickout(loginId)`   | `StpUtil.kickout(loginId)`              |
| —                                              | `SubjectKit.refresh()`          | `StpUtil.renewTimeout()`                |

---

## 三、Spring Security → sa-token 迁移

### 3.1 阶段 1：接入 ddd4j-core

#### 步骤 1：添加依赖

```xml
<!-- 保留原有 Spring Security 依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- 新增 ddd4j-auth（与 Shiro 迁移相同的三个依赖） -->
<dependency>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-core</artifactId>
    <version>${ddd4j.version}</version>
</dependency>
<dependency>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-auth-security</artifactId>
    <version>${ddd4j.version}</version>
</dependency>
<dependency>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-auth-spring</artifactId>
    <version>${ddd4j.version}</version>
</dependency>
```

#### 步骤 2：业务代码改造（Spring Security API → SubjectKit）

```java
// ====== 改造前（直接调 Spring Security API）======
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
Object principal = auth.getPrincipal();
Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
boolean isAdmin = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

// ====== 改造后（统一调 SubjectKit）======
AuthPrincipal principal = SubjectKit.getPrincipal();
boolean isAdmin = SubjectKit.hasRole("admin");
```

### 3.2 阶段 3：切换到 sa-token

```xml
<!-- 移除 Spring Security 依赖 -->
<!-- <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency> -->

<!-- 引入 sa-token -->
<dependency>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-auth-satoken</artifactId>
    <version>${ddd4j.version}</version>
</dependency>
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-spring-boot3-starter</artifactId>
</dependency>
```

### 3.3 Spring Security → sa-token API 对照表

| Spring Security API                                                     | SubjectKit API（统一）                                      | sa-token 底层                             |
|-------------------------------------------------------------------------|---------------------------------------------------------|-----------------------------------------|
| `SecurityContextHolder.getContext().setAuthentication(auth)`            | `SubjectKit.login(AuthRequest)`                         | `StpUtil.login(id)`                     |
| `SecurityContextHolder.clearContext()`                                  | `SubjectKit.logout()`                                   | `StpUtil.logout()`                      |
| `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` | `SubjectKit.getPrincipal()`                             | `StpUtil.getSession().get("principal")` |
| `auth.getAuthorities()` 比对                                              | `SubjectKit.hasRole(r)`                                 | `StpUtil.hasRole(r)`                    |
| `@PreAuthorize("hasRole('ADMIN')")`                                     | `@SaCheckRole("admin")` 或 `SubjectKit.hasRole("admin")` | sa-token 注解                             |
| `@PreAuthorize("hasAuthority('user:add')")`                             | `@SaCheckPermission("user:add")`                        | sa-token 注解                             |
| `Authentication.isAuthenticated()`                                      | `SubjectKit.isLogin()`                                  | `StpUtil.isLogin()`                     |
| `RememberMeAuthenticationToken`                                         | `SubjectKit.getSubject().isRemembered()`                | sa-token 记住我                            |

---

## 四、统一权限数据源（关键步骤）

无论底层是 Shiro / Security / sa-token，**权限校验都委托 `SubjectDataProvider`**，保证行为一致：

```java
// 业务实现 SubjectDataProvider（一次性配置）
SubjectKit.setDataProvider(new SubjectDataProvider() {
    @Override
    public List<String> getPermissionList(AuthPrincipal principal) {
        // 从数据库/缓存查询用户权限
        return permissionService.findByUserId(principal.getUserId());
    }
    @Override
    public List<String> getRoleList(AuthPrincipal principal) {
        return roleService.findByUserId(principal.getUserId());
    }
});
```

**迁移期间无需改动权限数据源**——切换底层鉴权框架时，权限数据源实现保持不变。

---

## 五、多账号体系迁移

### 5.1 Shiro 多 Realm → sa-token 多 StpLogic

```java
// Shiro 方式（复杂）：配置多个 Realm + ModularRealmAuthenticator
@Bean
public SecurityManager securityManager() {
    DefaultWebSecurityManager sm = new DefaultWebSecurityManager();
    List<Realm> realms = List.of(userRealm, adminRealm);
    sm.setRealms(realms);
    sm.setAuthenticator(new ModularRealmAuthenticator());
    return sm;
}

// sa-token 方式（简洁）：StpLogic 按 loginType 隔离
public static final StpLogic ADMIN = new StpLogic("admin");
public static final StpLogic USER = new StpLogic("user");

// ddd4j 统一调用
Subject adminSubject = SubjectKit.getSubject("admin");
adminSubject.login(AuthRequest.of(adminId).realm("admin"));
```

### 5.2 Spring Security 多 SecurityContext → sa-token 多 StpLogic

```java
// Spring Security 方式：多 SecurityContextSource + OAuth2
// sa-token 方式：同上，StpLogic("admin") / StpLogic("user")
```

---

## 六、常见问题

### Q1：迁移后旧 Token 是否还有效？

**阶段 1-2**（双写运行）：旧 Token 仍有效，新旧 API 并存。
**阶段 3-4**（切换底层）：旧 Token 失效，用户需重新登录。

**建议**：在阶段 3 提供一个"旧 Token 换新 Token"的过渡接口，减少用户感知。

### Q2：Shiro 的 RememberMe 功能在 sa-token 中如何实现？

```java
// sa-token 记住我（登录时指定）
SubjectKit.login(AuthRequest.of(userId)
    .timeout(30 * 24 * 3600)  // 30 天
    .deviceType("web"));
```

### Q3：Spring Security 的方法级注解如何迁移？

| Spring Security                             | sa-token                         |
|---------------------------------------------|----------------------------------|
| `@PreAuthorize("hasRole('ADMIN')")`         | `@SaCheckRole("admin")`          |
| `@PreAuthorize("hasAuthority('user:add')")` | `@SaCheckPermission("user:add")` |
| `@PreAuthorize("isAuthenticated()")`        | `@SaCheckLogin`                  |
| `@Secured("ROLE_ADMIN")`                    | `@SaCheckRole("admin")`          |

### Q4：如何验证迁移后的权限校验行为一致？

```java
// 单元测试：验证 SubjectKit.hasPermission 在三框架下行为一致
@Test
void testPermissionAcrossFrameworks() {
    SubjectKit.setDataProvider(principal -> List.of("user:add", "user:delete"));

    // Shiro 底层
    // Security 底层
    // sa-token 底层
    // 三者结果应该一致
    assertTrue(SubjectKit.hasPermission("user:add"));
    assertFalse(SubjectKit.hasPermission("user:update"));
}
```

---

## 七、迁移检查清单

### 阶段 1 接入

- [ ] 添加 `ddd4j-core` + 对应鉴权适配依赖
- [ ] 实现 `SubjectDataProvider` 权限数据源
- [ ] 业务代码改为调 `SubjectKit.xxx()`
- [ ] 验证 `SubjectKit.getSubject()` 不抛异常
- [ ] 验证旧鉴权 API 仍可用

### 阶段 2 双写

- [ ] 新旧鉴权 API 并存运行
- [ ] 监控无异常
- [ ] 逐步替换所有旧 API 调用点

### 阶段 3 灰度

- [ ] 试点服务引入 sa-token
- [ ] 验证 `SubjectKit` 行为一致
- [ ] 提供旧 Token 换新 Token 接口

### 阶段 4 全量

- [ ] 移除旧鉴权依赖（Shiro / Spring Security）
- [ ] 统一 sa-token 配置
- [ ] 更新部署文档与监控告警
