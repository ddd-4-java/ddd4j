# Spring 商品交易示例

四个入口共用领域模型和仓储，启动时由 Flyway 迁移专用 MySQL 的四张业务表。[数据库配置与接口](../README.md)

| 模块                          | 默认端口 | 运行方式                          |
|-------------------------------|----------|-----------------------------------|
| `ddd4j-sample-spring`         | 8080     | 默认 MVC，启动参数可选 WebFlux    |
| `ddd4j-sample-spring-cqrs`    | 8081     | Spring 命令总线及独立订单快照投影 |
| `ddd4j-sample-spring-satoken` | 8082     | Sa-Token 原生登录、权限和退出     |
| `ddd4j-sample-spring-shiro`   | 8083     | Shiro Realm、Servlet 会话和权限   |

## 源码依赖与构建

选择当前分支对应的 JDK，并将 `JAVA_HOME` 指向该 JDK。三线均使用仓库 Maven Wrapper。表中的版本来自 R14 本线默认样例的归档
JAR。

| 分支  | JDK | `DDD4J_VERSION`           | 外部 `io.ddd4j.boot` 版本 | 实际 Spring Boot            | Spring Framework | Reactor |
|-------|-----|---------------------------|---------------------------|-----------------------------|------------------|---------|
| 1.0.x | 8   | `1.0.x.20260630-SNAPSHOT` | `2.7.x.20260630-SNAPSHOT` | 核心 2.7.7，自动配置 2.7.18 | 5.3.39           | 3.8.6   |
| 2.0.x | 17  | `2.0.x.20260630-SNAPSHOT` | `3.4.x.20260630-SNAPSHOT` | 3.5.16                      | 6.2.19           | 3.8.8   |
| 3.0.x | 21  | `3.0.x.20260630-SNAPSHOT` | `4.0.x.20260630-SNAPSHOT` | 4.0.8                       | 7.0.9            | 3.8.6   |

将 `DDD4J_VERSION` 设置为表中对应值。从源码准备构件时，先执行下方步骤，再使用准备好的 Maven 本地仓库从主仓库根目录打包默认示例。若配置的
Maven 仓库已能解析本线外部构件，可省略 `-Dmaven.repo.local` 参数。

```bash
./mvnw -Dmaven.repo.local="$DDD4J_MAVEN_REPO" \
  -pl ddd4j-samples/ddd4j-sample-spring -am \
  -Dmaven.test.skip=true -Dmaven.javadoc.skip=true package
```

从源码准备外部构件时，将 `DDD4J_MAVEN_REPO` 设置为独立 Maven 本地仓库的绝对路径，将 `DDD4J_BOOT_DIR` 设置为本线的外部
`ddd4j-boot` 源码目录。从主仓库根目录执行。第一步准备 Boot 源码依赖的主仓库父 POM、BOM 和框架构件，第二步准备四个 Spring
样例所需的 Boot 构件。

```bash
./mvnw -Dmaven.repo.local="$DDD4J_MAVEN_REPO" \
  -pl :ddd4j-parent,:ddd4j-bom,:ddd4j-core,:ddd4j-runtime-spring,:ddd4j-web-webmvc,:ddd4j-web-webflux,:ddd4j-auth-satoken,:ddd4j-auth-shiro,:ddd4j-auth-spring \
  -am -Dmaven.test.skip=true -Dmaven.javadoc.skip=true install
(
  cd "$DDD4J_BOOT_DIR"
  sh ./mvnw -Dmaven.repo.local="$DDD4J_MAVEN_REPO" \
    -pl ddd4j-boot-core,ddd4j-boot-web/ddd4j-boot-web-webmvc,ddd4j-boot-web/ddd4j-boot-web-webflux,ddd4j-boot-auth/ddd4j-boot-auth-satoken,ddd4j-boot-auth/ddd4j-boot-auth-shiro \
    -am -Dmaven.test.skip=true -Dmaven.javadoc.skip=true install
)
```

- Java 8 R14 外部 Boot 源码提交为 `72a51123347927b48e0caa144c5e0eddbc729d02`。安装记录位于
  `R14-evidence/jdk8-boot2-*-source-install`。Sa-Token 样例使用 Boot 2 Servlet starter 1.44.0。
- Java 17 S01 外部 Boot 源码和 Maven 仓库位于计划证据根目录下的 `S01-build`。外部源码 POM 声明 Boot 3.4.13，默认样例实际打包的
  Boot 版本见上表。旧 `spring-extension` 二进制仍调用已移除的 `Mono.subscriberContext`。在任意对应源码目录内，用相同 Maven
  本地仓库执行 `sh ./mvnw -Dmaven.repo.local="$DDD4J_MAVEN_REPO" -Dtest=ReactiveRequestContextFilterTest install` 安装
  Easy4J `spring-extension` 的 `feature/2.0.x` 源码提交 `8386dec0e00ed6b23a853188db2028c3eb92bedc`。S01 测试使用 Sa-Token
  1.45.0 和 Shiro 3.0.2。S01 外部认证模块的完整 Javadoc 构建因原有标签缺失失败。
- Java 21 R14 使用外部 Boot `4.0.x` 源码提交 `f5ae36355e83259eded49ba018a8e3c8d9ecacab`。源码与安装记录位于
  `R14-evidence/boot4-source-origin.json` 和 `R14-evidence/boot4-source-install-online.json`。

上述跳过测试和 Javadoc 的命令只用于准备构件。继续使用同一个 Maven 本地仓库验证四个样例。

```bash
./mvnw -Dmaven.repo.local="$DDD4J_MAVEN_REPO" \
  -pl ddd4j-samples/ddd4j-sample-spring,ddd4j-samples/ddd4j-sample-spring-cqrs,ddd4j-samples/ddd4j-sample-spring-satoken,ddd4j-samples/ddd4j-sample-spring-shiro \
  -am verify
```

## 启动与调用

配置 `CATALOG_JDBC_URL`、`CATALOG_DB_USER`、`CATALOG_DB_PASSWORD` 后启动各模块的可执行 JAR。WebFlux 入口增加
`--spring.main.web-application-type=reactive --spring.profiles.active=catalog-trade`。

CQRS 的写命令由容器中的 `SpringCommandBus` 分发。调用 `POST /orders/catalog/projections/orders/{orderNo}`
后，查询可读取当前的已提交订单快照。进程重启后可重新触发该操作重建视图。

鉴权入口的 `alice` 为只读账号，`writer` 有商品建档、下单及确认支付权限。两个演示账号的密码均为 `demo-password`。Sa-Token
使用原生会话令牌，Shiro 使用 Servlet Cookie。登录和退出继续使用 `/auth/login` 和 `/auth/logout`。
