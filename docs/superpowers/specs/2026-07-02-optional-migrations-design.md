# 按需迁移项设计

- **日期**：2026-07-02
- **作者**：ddd4j 架构团队
- **状态**：参考文档

## 1. 目标与范围

记录从旧 ddd4j（base-* 1.x）中识别出的、有借鉴价值但因依赖较重而未自动迁移的设计。业务项目按需自行引入对应实现。

## 2. GlobalFeignErrorAdvice（Feign 错误解码器）

**来源**：旧 `base-web/src/main/java/.../core/GlobalFeignErrorAdvice.java`

**功能**：将 Spring Cloud Feign 调用异常统一转换为 `ServiceException`，避免业务代码到处捕获 Feign 原生异常。

**未迁移原因**：依赖 `spring-cloud-starter-openfeign`（`FeignErrorDecoderFactory`），属于 Spring Cloud 生态组件，非通用 Web 基础设施。

**业务项目自行引入方式**：

1. 在业务项目 pom 中引入 `spring-cloud-starter-openfeign`
2. 复制实现到业务项目的 `infrastructure/feign/` 目录
3. 注册为 Bean

## 3. RedisKit / GCacheKit（旧版 Redis 工具）

**来源**：旧 `base-kit/src/main/java/.../cache/RedisKit.java` / `GCacheKit.java`

**未迁移原因**：旧版使用 ThreadLocal 管理裸 `Jedis` 连接（无连接池、易泄漏），且硬编码 `SpringContext.getEnv()` 读取配置，设计已过时。

**替代方案**：新 ddd4j 提供 `ddd4j-kit/cache/Cache` SPI 抽象（`CaffeineCache` / `GuavaCache` / `HutoolCache` 实现）。Redis 场景建议：

- Spring 项目：直接使用 Spring Data Redis 的 `RedisTemplate`
- 自定义实现：实现 `io.ddd4j.kit.cache.Cache` 接口提供 `RedisCache`
