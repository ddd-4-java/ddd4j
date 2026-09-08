# TrueLicense 4.x 框架消费侧审计

日期：2026-09-08。性质：架构审计与当前维护线收敛记录。本文件承接 [TrueLicense 4.x 迁移指南](truelicense4.md)。

## 结论矩阵

| 框架仓库/维护线 | 当前许可证实现 | 旧1.33残留 | 新4.x API适配 | 结论 |
|---|---|---|---|---|
| ddd4j-boot 2.7.x（当前checkout，JDK8/ddd4j1） | ddd4j-auth-license | 已删除叶模块core+xml直接依赖 | 已透传signatureAlgorithm | 目标测试通过；聚合模型仍有独立阻塞 |
| ddd4j-boot 2.3–2.6、3.0–3.5、4.0–4.1 | ddd4j-auth-license | 分支审计存在core+xml直接依赖及BOM管理 | 分支审计未透传 | 尚未修改和验证 |
| ddd4j-quarkus feature/4.0.x（当前checkout） | ddd4j-extension-license | 已删除无用core1.33直接依赖及management | 不应切换到auth-license | 真实签发/安装/验签通过 |
| ddd4j-quarkus feature/3.3.x | ddd4j-extension-license | 分支审计存在无用core1.33 | 不应切换到auth-license | 尚未修改和验证 |
| ddd4j-javalin feature/6.7.x、7.1.x、7.2.x | ddd4j-extension-license | 无 | 与TrueLicense4无关 | 当前维护线无需变更 |
| ddd4j-cloud 2020.0.x | 未发现许可证集成 | 无匹配 | 无 | 无直接变更；受Boot传递边界影响 |

这里的“阻塞”只表示框架消费侧尚未闭合，不推翻三条ddd4j底座中已完成的4.1.4迁移。

## Boot：13条维护线是同一问题

config/consistency/ddd4j-boot-build-matrix.tsv定义13条线：2.3–2.7对应JDK8/ddd4j1，3.0–3.5对应JDK17/ddd4j2，4.0–4.1对应JDK21/ddd4j3。对这些本地branch执行只读git grep，每条线都同时包含：

- ddd4j-boot-auth-license依赖io.ddd4j:ddd4j-auth-license；
- 同一叶模块重复声明de.schlichtherle.truelicense:truelicense-core和truelicense-xml；
- ddd4j-boot-dependencies把两个旧坐标固定为1.33。

13条线的DefaultLicenseAutoConfiguration.java SHA-256均为be9c314bd941ab4a40a0450d53fad2c4ffdbabe09c75917c61b46a45d68e3ca4。它已通过Binder把LicenseProperties绑定到POJO，但创建LicenseVerify仍调用五参数构造器，因此新字段signatureAlgorithm即使绑定也不会透传，始终回落到历史默认SHA1withDSA。

修复前2.7.x叶模块runtime dependency tree为：

    io.ddd4j.boot:ddd4j-boot-auth-license:2.7.x.20260630-SNAPSHOT
    +- io.ddd4j:ddd4j-auth-license:1.0.x.20260630-SNAPSHOT
    +- de.schlichtherle.truelicense:truelicense-core:1.33
    \- de.schlichtherle.truelicense:truelicense-xml:1.33

修复后叶模块只直接依赖 `io.ddd4j:ddd4j-auth-license:1.0.x.20260630-SNAPSHOT`，其运行依赖
解析为 `global.namespace.truelicense:*:4.1.4`，没有旧group。根聚合器当前另因
`org.glassfish.expressly:expressly` 缺失版本而模型失败；这是独立红项，不能用来扩大叶模块结论。

当前 2.7.x 已完成上述第2至第4项：叶模块删除旧core/xml，自动配置调用六参数构造并透传
`LicenseProperties.getSignatureAlgorithm()`；新增 Binder 属性测试先观察到配置值被错误回落为
`SHA1withDSA`，修复后3项测试全部通过。对应1.0.x底座制品仅安装到本地Maven仓库用于消费验证，
没有发布远端。根聚合器仍被 `org.glassfish.expressly:expressly` 缺失版本阻断，不能把叶模块通过
表述成2.7.x整线通过。

Boot最小闭合方案仍须同步应用其余12条维护线：

1. 先确保对应ddd4j1/2/3的dependencies、parent、auth聚合POM和auth-license制品都已发布/可消费；只发布叶JAR不足以证明模型可解析。
2. 从Boot叶模块删除旧core/xml直接依赖，从Boot dependencies删除旧版本属性和management条目。
3. 自动配置改为六参构造，并从LicenseProperties.getSignatureAlgorithm()透传。默认SHA1保持旧证书行为；显式SHA256用于新证书。
4. 增加Binder真实属性测试：默认值、license.signature-algorithm=SHA256withDSA透传、空白值失败；高层安装行为仍由底座测试负责。
5. 每条线用自己的JDK/Maven验证runtime tree无旧group、自动配置测试、完整模块回归和维护矩阵门禁。不能只改2.7.x后声称13线完成。

## Quarkus：无用依赖，不是TrueLicense实现

feature/3.3.x与feature/4.0.x的Ddd4jLicenseQuarkusConfig源码SHA均为c119f811969b54c519cbc3bc1ea3faaf9feeff34e2711407ac2dbe945639f2d9。配置和POM都使用io.ddd4j:ddd4j-extension-license，这是独立签名协议，不依赖auth-license的TrueLicense引擎。

但两条线的Quarkus auth-license叶POM又直接声明旧truelicense-core，对应dependencies BOM也管理1.33。修复前feature/4.0.x、JDK21/Maven4叶模块runtime tree为：

    io.ddd4j.quarkus:ddd4j-quarkus-auth-license:4.0.x.20260630-SNAPSHOT
    +- io.ddd4j:ddd4j-extension-license:3.0.x.20260730-SNAPSHOT
    \- de.schlichtherle.truelicense:truelicense-core:1.33
       \- de.schlichtherle.truelicense:truelicense-xml:1.33

feature/4.0.x 当前checkout已删除叶模块旧依赖和dependencies旧管理项，并把
`ddd4j-extension-license` 补入dependencies/BOM集中管理，叶模块不再声明版本。原有
`LicenseEnabledEndToEndQuarkusTest` 实际只生成密钥库、没有签发许可证，而且明确允许安装失败后
仅断言Bean存在；本轮已修正为Quarkus启动前真实签发，启动后断言安装成功及验签成功。该测试与
默认关闭测试合计2项通过，日志确认“License证书生成成功”和“License证书安装成功”。

feature/3.3.x 尚未修改。4.0.x 运行仍输出大量Maven4有效模型告警及Maven settings解析告警；这些是
独立发布/模型治理问题，不能由许可证测试通过抵消。

## Javalin、Cloud与CodeGraph边界

Javalin feature/6.7.x、7.1.x、7.2.x的auth-license模块均只依赖ddd4j-extension-license；当前6.7.x叶runtime tree也仅解析该模块，没有旧TrueLicense坐标。master仍可搜索到旧坐标，但不属于上述三条发布维护线，应作为陈旧分支治理。

Cloud 2020.0.x的当前源码/POM没有auth-license、extension-license或TrueLicense匹配。它不需要直接改许可证模块，但若组合使用Boot auth-license，仍会继承Boot消费侧未闭合的问题。

旧目标仓库 `/Users/wandl/workspaces/workspace-bmgw/codeup/ddd4j` 与实际迁移参照
`/Users/wandl/workspaces/workspace-bmgw/codeup/cloud-agents` 当前也没有许可证、LicenseVerify、
auth-license、extension-license 或 TrueLicense 的源码/POM/配置匹配。因此 TrueLicense 4迁移不是
“替代旧BMGW能力”的行为对等前置条件，而是新底座新增能力的依赖合规与框架集成问题。
这两个仓库没有 `.codegraph`，遵循项目指令未擅自初始化索引；结论来自当前checkout的受限文本审计。

Boot首次CodeGraph查询返回了当前文件树已不存在的ddd4j-boot-extensions旧许可证源码。同步前状态只报告一个POM修改，却实际同步出Added18/Modified103/Removed19，共1197节点。同步后查询只返回当前ddd4j-boot-auth-license自动配置，和文件树一致。因此这里的CodeGraph结论来自同步后的图谱；分支POM矩阵使用git grep branch补充，因为单一CodeGraph索引只代表当前checkout，不能证明13条branch。

## 当前边界与顺序

- 本轮修改了当前 Boot 2.7.x 与 Quarkus feature/4.0.x；Quarkus既有Web改动、临时目录和Cloud现有脏改均保留。
- ddd4j三线迁移代码已被其他操作纳入当前commit，但最终指南状态仍有工作区修改；本任务没有执行提交、推送或发布。
- 当前不能声称“框架组合已完全迁移TrueLicense4”或“整体生产就绪”：Boot其余12线、Quarkus 3.3.x、clean-cache消费和聚合模型仍未闭合。
- 后续顺序应为：修复Boot聚合模型 → 其余Boot维护线移除旧库并透传算法 → Quarkus 3.3.x删除无用库 → Javalin/Cloud无变更验证 → clean-cache消费者验证。
