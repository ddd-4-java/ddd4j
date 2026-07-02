# 按需迁移项（Optional Migrations）

本文档记录从旧 ddd4j（base-* 1.x）中识别出的、**有借鉴价值但因依赖较重而未自动迁移**的设计。
业务项目按需自行引入对应实现。

## 1. GlobalFeignErrorAdvice（Feign 错误解码器）

**来源**：旧 `base-web/src/main/java/.../core/GlobalFeignErrorAdvice.java`

**功能**：将 Spring Cloud Feign 调用异常统一转换为 `ServiceException`，避免业务代码到处捕获 Feign 原生异常。

**未迁移原因**：依赖 `spring-cloud-starter-openfeign`（`FeignErrorDecoderFactory`），属于 Spring Cloud 生态组件，非通用 Web
基础设施。强行引入会污染 `ddd4j-web-webmvc` / `ddd4j-runtime-spring` 的依赖树。

**业务项目自行引入方式**：

1. 在业务项目 pom 中引入 `spring-cloud-starter-openfeign`；
2. 复制以下实现到业务项目的 `infrastructure/feign/` 目录：

```java
package com.example.infra.feign;

import io.ddd4j.core.exception.BizRuntimeException;
import feign.codec.ErrorDecoder;
import org.springframework.cloud.openfeign.FeignErrorDecoderFactory;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;

public class GlobalFeignErrorAdvice implements FeignErrorDecoderFactory {

    @Override
    public ErrorDecoder create(Class<?> type) {
        return (methodKey, response) -> {
            String reason = response.reason();
            String serviceName = response.request().requestTemplate().feignTarget().name();
            int status = response.status();
            String errorMsg = String.format("status=%s,%s:%s", status, serviceName,
                    reason == null || reason.isEmpty() ? "请求异常" : reason);
            if (response.body() != null) {
                try {
                    Reader reader = response.body().asReader(Charset.defaultCharset());
                    errorMsg = toString(reader);
                    return new BizRuntimeException(errorMsg);
                } catch (Exception ignore) {
                }
            }
            return new BizRuntimeException(errorMsg);
        };
    }

    public static String toString(Reader input) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[1024 * 4];
        int n;
        while (-1 != (n = input.read(buffer))) {
            sb.append(buffer, 0, n);
        }
        return sb.toString();
    }
}
```

3. 注册为 Bean：`@Bean public GlobalFeignErrorAdvice globalFeignErrorAdvice() { return new GlobalFeignErrorAdvice(); }`

## 2. RedisKit / GCacheKit（旧版 Redis 工具）

**来源**：旧 `base-kit/src/main/java/.../cache/RedisKit.java` / `GCacheKit.java`

**未迁移原因**：旧版使用 ThreadLocal 管理裸 `Jedis` 连接（无连接池、易泄漏），且硬编码 `SpringContext.getEnv()` 读取配置，设计已过时。

**替代方案**：新 ddd4j 提供 `ddd4j-kit/cache/Cache` SPI 抽象（`CaffeineCache` / `GuavaCache` / `HutoolCache` 实现）。Redis
场景建议：

- Spring 项目：直接使用 Spring Data Redis 的 `RedisTemplate`；
- 自定义实现：实现 `io.ddd4j.kit.cache.Cache` 接口提供 `RedisCache`。
