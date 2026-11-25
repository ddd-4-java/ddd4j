# Validation 整合实施记录

## REQ Tika 兼容功能三线适配（2026-11-25）

- [x] 逐项登记来源提交和文件差异，区分已覆盖功能、待移植功能、版本适配及本地缓存。
- [x] 先迁入来源行为测试，证明版本分支缺口，再复用已有流工具补齐实现。
- [x] 对齐 CI 与接入文档，保留原有依赖约束和强制覆盖率门禁。
- [x] 三线上游完整验证、DDD4J 消费回归、提交与工作区保护核验。


验收证据：`../../reports/2026-11-25-validation-req-parity.md`。

## 数据权限下沉到 Easy4j（2026-11-25）

- [x] 证明缺失 Provider 的默认放行缺陷，补齐上游及 Spring/OTel 行为测试。
- [x] 上游唯一实现；DDD4J 删除重复类型并直接集成，更新接入说明。
- [x] 补齐上游 2.0.x 必要的既有文件 API 前置；两仓三线适配与验证。
- [x] 校验产物、分支一致性、工作区保护，记录最终证据。

验收证据：`../../reports/2026-11-25-data-permissions-component.md`。

规格：`../specs/2026-11-24-validation-integration.md`。

- [x] 使用 CodeGraph 与当前源码核对 Easy4j 文件校验、上传模型、SPI 和 DDD4J 调用方。
- [x] 添加跨入口行为测试，确认目标失败。
- [x] 上游提取唯一公共校验服务，注解入口改为适配。
- [x] DDD4J 复用公共服务；迁移 DataScope，调整 reactor/BOM/消费者和文档。
- [x] 执行两仓测试与受影响 clean verify、检查构件内容及重复实现，记录验证限制。


本地验收证据：上游 Corretto 17 / Maven 3.9.16 `mvn -o clean install` 131 项测试通过；
DDD4J `./mvnw -o -pl :ddd4j-extension-validation,:ddd4j-auth-datascope,:ddd4j-extension-otel -am clean verify`
12 项 reactor、903 项测试通过，均无失败、错误或跳过。无 record 检查及其 7 项自测通过。
CodeGraph 上游索引在用户授权后创建并检查为最新，72 文件、958 节点、1,638 关系。
具体契约、产物哈希、覆盖率告警及范围限制见 [整合报告](../../reports/2026-11-24-validation-integration.md)。

## 直接集成修正（用户确认取消旧名字包装）

- [x] 删除 12 个 DDD4J 文件包装类型，测试和调用方改用 Easy4j 原生 API。
- [x] 删除 Web 四套重复约束及空模块，迁移通用约束测试及当前版本证据引用。
- [x] 移除包装层专用 ddd4j-kit 依赖，保留文件检测所需解析器。
- [x] 更新接入文档，执行受影响 clean verify 与最终 JAR 导出检查。

以上为同一规格的后续修正；前述 903 项测试是修正前证据，不代表本轮验收。

本轮：12 个 reactor clean verify 成功，897 项测试零失败/错误/跳过；validation 13 项。最终 JAR 只有 3 个 DataScope 类型；纯运行 classpath 的真实 DOCX/XLSX 校验通过。记录见报告“后续直接集成修正”。
