# Ddd4j Boot 架构与模块关系

## 核心目的
- 基于 Spring Boot 3.5.x 的工程脚手架，统一依赖版本、标准响应与异常、自动配置与示例工程，降低新服务的搭建成本。

## 顶层结构
- 根聚合：`ddd4j/pom.xml`
- 管理三件套：
  - 版本对齐：`ddd4j-bom/pom.xml`
  - 依赖声明：`ddd4j-dependencies/pom.xml`
  - 打包父：`ddd4j-parent/pom.xml`
- 核心能力：`ddd4j-core`
- 组件集：`ddd4j-cmpt/*`
- 示例集：`ddd4j-samples/*`

## 版本与构建
- Java：17；Spring Boot：3.5.6；Spring Framework：6.2.x（根 POM）
- Maven 多模块聚合，统一 BOM 与插件管理（编译、源码、Javadoc、签名、Release、打包与 Docker）

## 模块关系（Mermaid）
```mermaid
graph TD
  A[ddd4j (聚合)] --> B[ddd4j-bom]
  A --> C[ddd4j-dependencies]
  A --> D[ddd4j-parent]
  A --> E[ddd4j-core]
  A --> F[ddd4j-cmpt]
  A --> G[ddd4j-samples]

  F --> F1[cmpt-webmvc]
  F --> F2[cmpt-webflux]
  F --> F3[cmpt-jackson]
  F --> F4[cmpt-crypto]
  F --> F5[cmpt-kafka]
  F --> F6[cmpt-license]
  F --> F7[cmpt-satoken]
  F --> F8[cmpt-logs]
  F --> F9[cmpt-datascope]
  F --> F10[cmpt-external]
  F --> F11[cmpt-akka]

  G --> G1[sample-druid]
  G --> G2[sample-hikaricp]
  G --> G3[sample-r2dbc-webflux]
  G1 --> G1a[amqp/kafka/rocketmq/mqtt]
  G2 --> G2a[amqp/activemq/kafka/rocketmq]
```

## 核心能力速览
- 响应模型与状态码：`ddd4j-core/src/main/java/io/ddd4j/boot/core/ApiRestResponse.java`、`ddd4j-core/src/main/java/io/ddd4j/boot/core/ApiCode.java`
- Service/Mapper/Controller 基类：`ddd4j-core/src/main/java/io/ddd4j/boot/core/service/BaseServiceImpl.java`、`ddd4j-core/src/main/java/io/ddd4j/boot/core/mybatis/mapper/BaseMapper.java`、`ddd4j-core/src/main/java/io/ddd4j/boot/core/web/BaseController.java`
- 全局异常（MVC/WebFlux）：`ddd4j-cmpt/ddd4j-cmpt-webmvc/src/main/java/io/ddd4j/boot/cmpt/webmvc/webmvc/GlobalExceptionHandler.java`、`ddd4j-cmpt/ddd4j-cmpt-webflux/src/main/java/io/ddd4j/boot/cmpt/webflux/handler/GlobalExceptionHandler.java`
- 自动配置注册：`ddd4j-cmpt/*/src/main/resources/META-INF/spring.factories`

## 代码参考
- 根聚合与版本：`ddd4j/pom.xml:31-39`
- MVC 基础配置：`ddd4j-cmpt/ddd4j-cmpt-webmvc/src/main/java/io/ddd4j/boot/cmpt/webmvc/DefaultWebMvcConfiguration.java:53-76`
- 响应模型：`ddd4j-core/src/main/java/io/ddd4j/boot/core/ApiRestResponse.java:118-137`
- 全局异常（MVC）：`ddd4j-cmpt/ddd4j-cmpt-webmvc/src/main/java/io/ddd4j/boot/cmpt/webmvc/webmvc/GlobalExceptionHandler.java:72-81`

