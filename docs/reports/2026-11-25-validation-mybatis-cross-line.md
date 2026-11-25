# Validation / MyBatis 跨分支落地验证（2026-11-25）

## 基线与范围

本分支：`feature/3.0.x`，JDK 21。本次以 DDD4J `feature/2.0.x` 的 `b9210e7baf8858f4d75c51f19efd9d59d87eecb8` 为逻辑基线，包含最新的 StringUtils 依赖移除和类型转换修正。Easy4j 组件取本次任务已有的公开文件校验 API、数据权限处理器及查询结果回调变更。仅同步这组 Validation / MyBatis 整合逻辑；原有 CQRS/projection 等无关工作不在本次范围内。

延续 `docs/superpowers/specs/2026-11-24-validation-integration.md` 与 `2026-11-24-mybatis-enhance-integration.md`。计划中早先的 2.0.x 测试数字为历史背景，本分支验收以本报告为准。

## 最终行为

- `ddd4j-data-datascope` 合入 `ddd4j-extension-validation`，DataScope 的原 Java 包名保留；模块依赖改为合并后的 artifact。
- 通用约束及文件校验直接消费 `io.github.easy4j:validation-api-extension`，删除 DDD4J 重复包装及 `ddd4j-web-validation` 模块。最终 validation JAR 只导出 3 个 DataScope 类型。
- 原生 MyBatis 与 MyBatis-Plus 均保留 `BaseRepositoryImpl` 业务继承入口；聚合仓储集中实现，`M extends AggregateRoot<ID>` 约束防止 ID 泛型错配。
- 领域批量操作直接复用 MyBatis-Plus Mapper 批量能力，回填模型并清理原会话缓存；相关行为、ID 编译正反例和真实 MySQL 回归全部通过。
- 数据权限、加解密、查询结果回调使用 Easy4j 组件；Guice 接入同步更新。原生 MyBatis 使用明确的 SQL/参数绑定扩展点。

## 本分支适配

- Java 21 保留本分支 Jakarta、Jackson 3、JSqlParser 等依赖线；SQL 观测测试使用本分支实际 Jackson 包名。
- Tika Microsoft 解析模块采用本分支现有上游使用的 3.3.2；没有将 2.0.x 的 Tika 4 或其他 BOM 版本整体覆盖到本分支。

## 验证结果

| 项目 | 命令范围 | 测试数 | 失败 / 错误 / 跳过 |
| --- | --- | ---: | --- |
| validation-api-extension | clean install | 127 | 0 / 0 / 0 |
| mybatis-plus-enhance | clean install，全组件 reactor | 166 | 0 / 0 / 0 |
| DDD4J | 下列 6 个模块及其依赖，26 个 reactor 项目 | 1297 | 0 / 0 / 0 |
| 无 record 检查器自测 | unittest | 5 | 0 / 0 / 0 |

```sh
bash mvnw -o -pl :ddd4j-data-mybatisplus,:ddd4j-data-mybatis,:ddd4j-runtime-guice,:ddd4j-extension-validation,:ddd4j-auth-datascope,:ddd4j-extension-otel -am clean verify
python3 scripts/check_no_records.py
python3 -m unittest discover -s scripts -p test_check_no_records.py
git diff --cached --check
```

DDD4J 包含 MyBatis-Plus 67 项（其中真实 MySQL 10 项）、原生 MyBatis 10 项、Guice 101 项、Validation 13 项及其他受影响依赖回归。完整 clean verify 通过；最后去掉重复空值 guard 后，使用相同模块范围再次 verify，通过 1297 项。

产物核验：两个仓储 JAR 均包含 `BaseRepositoryImpl`；validation JAR 恰有 3 个 DataScope class；字节码 major 为 65。本轮安装到本地 Maven 缓存的上游 JAR 与构建输出 SHA-256 一致，DDD4J 通过正常 Maven 依赖解析消费这些产物。

## 兼容性与证据边界

- 保留本分支 JDK、版本号及必要依赖差异。已删除模块/包装类型的调用方需按模块 README 迁移为组件 API；没有保留重复实现作为别名层。
- 上游 MyBatis-Plus Enhance 原有覆盖率告警仍存在（Core 44%、Extension 71%、Spring 19%）；未改变门禁，构建成功不代表达到整体 90%。
- 本次是本地组件及受影响模块回归，不是全仓库三线收敛、远端 CI 或生产验证。仓库既有三线策略/场景配置仍含已删除包装类型的旧引用，未在本次扩大为全套收敛治理。
- 未推送、未部署、未发布远端 Maven 构件。构建日志位于同级交付目录 `validation-mybatis-parity-20261125` 的 `ddd4j-3.0.x-verify.log`、`validation-3.0.x-build.log`、`enhance-3.0.x-build.log`；交付 manifest 记录分支起点与最终本地提交。
