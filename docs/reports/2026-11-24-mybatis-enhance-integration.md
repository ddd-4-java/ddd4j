# MyBatis 双链集成交付记录

状态：第三批在 feature/2.0.x 既定 JDK 17 基线下完成默认 clean verify 验证。没有提交、推送、部署或发布。可选 90% 覆盖率目标未达标，不将其标为通过。

## 最终验证

使用 Amazon Corretto 17.0.21，执行：

```sh
JAVA_HOME=<Corretto17_HOME> ./mvnw \
  -pl ddd4j-data/ddd4j-data-mybatis,ddd4j-data/ddd4j-data-mybatisplus,ddd4j-runtime/ddd4j-runtime-guice \
  -am clean verify
```

14 个 Maven 项目全部成功，936 项测试，0 失败、0 错误、0 跳过。包含真实 MySQL 8.4 的 8 项回归。原生模块 9 项、MP 模块 51 项、Guice 模块 117 项；其余为受影响依赖测试。Javadoc 和 JaCoCo report 均执行成功。没有使用 skipTests、maven.javadoc.skip、忽略 Javadoc 告警或跳过 JaCoCo 的参数。

| 独立验证 | 命令范围 | 结果 |
|---|---|---|
| mybatis-enhance | JDK 17，mvn -o clean verify | 69 项通过；实际消费 agent 参数并生成报告 |
| mybatis-plus-enhance | JDK 17，mvn -o clean verify | 129 项通过 |
| Boot MyBatis 目标模块 | JDK 17，./mvnw -f ddd4j-boot-data/ddd4j-boot-data-mybatis/pom.xml clean verify | 3 项通过；在线 dependency-check 执行，Javadoc JAR 和 JaCoCo 报告生成 |

已有原生及 MP 加密、签名、缓存、租户、事务和 SQL 观测验证继续保留。集合 Mapper 参数回填新增用例先因回填结果为空失败，改为复用上游 ParameterUtils 后通过最终完整回归。

## Javadoc 修复与基线

- 从 ApiIdempotent.value 的方法注释移除非法 @author 标签，类型级作者信息保留。
- 补齐 Boot 自动配置及异常处理器的 @param、@return 与 getter 注释，不改运行逻辑。
- doclint=all、failOnWarnings=true、failOnError=true 均保留，正式 JDK 17 构建无 Javadoc 告警。
- 早期沿用主机 JDK 21 构建时，还出现了 JDK 21 对 Lombok 源码模型隐式构造器的告警。本轮按项目已经确定的 2.0.x/JDK 17 基线交付，不宣称 JDK 21 文档构建通过；未为规避这些告警改变工具类构造行为。

## JaCoCo 修复与门禁证据

JaCoCo 对第三方 JSqlParser 生成的 CCJSqlParserTokenManager 插桩时会超过 JVM 方法大小限制。修复只作用于 prepare-agent：排除该一个第三方生成类。report/check 未排除任何本项目类，阈值未改变。实际 argLine 已携带该精确排除项，最终构建日志没有 MethodTooLargeException 或 IllegalClassFormatException。

Boot 模块和 mybatis-enhance 原固定 argLine 会覆盖 agent 参数，导致测试执行而报告没有数据。现保留原 JVM 参数并消费 @{argLine}，实际生成 jacoco.exec 与 XML/HTML 报告。

90% 门禁定义在 ddd4j-dependencies 的 coverage profile，仍为 LINE COVEREDRATIO >= 0.90。独立临时负例继承该实际配置，覆盖率 50%，以预期退出码 1 被 check-coverage 阻止，证明规则仍有效。

默认 verify 不激活 coverage profile。当前两个 DDD4J 模块 LINE 覆盖率为原生 33.23%、MP 52.00%，并未达到 90%；不将默认 verify 成功外推成 90% 覆盖率通过。两套上游组件既有 JaCoCo check 是告警模式，本轮未修改其阈值或失败策略，它们的覆盖率告警也保留在原始日志中。

## 适配边界复核

见 [适配边界审计](2026-11-24-mybatis-adapter-audit.md)。审查覆盖两个模块全部生产 Java 类型，并对照两套增强组件。没有第二套密码算法、密文格式、增强链、SQL 租户改写、分页解析、i18n 或 TypeHandler。保留内容是 DDD4J 领域仓储/Query/聚合填充、ThreadContext/CryptoStrategy 桥接、组件装配和历史公开契约。

进一步移除了聚合填充适配器自行遍历 Mapper 参数的逻辑，直接使用上游 ParameterUtils.extractParameters。旧 SQL 观测 DTO/SPI 保留历史 JSON 和调用兼容性，不承担执行、计时或 SQL 改写职责。

## 证据与交付边界

- 机器可读摘要：2026-11-24-mybatis-third-batch-verification.json。
- 完整日志、两个模块的 JaCoCo XML、源文件摘要和负例：/Users/wandl/Documents/Codex/2026-11-24/mybatis-third-batch-verification/。
- git diff --check 和禁止 record 检查通过。
- 验证为当前框架源码、本地数据库及容器验证；未执行真实业务应用迁移或生产验收。
- 原生 Query 需要 XML/注解 SQL 映射，JSqlParser AST 3.1 到 5.3 的直接消费者需要重新编译。原生租户插件不支持的复杂写入会明确拒绝；原生注解权限插件仅处理 SELECT。
