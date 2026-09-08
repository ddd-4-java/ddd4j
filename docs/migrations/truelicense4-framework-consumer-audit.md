# TrueLicense 4.x 框架消费侧审计

日期：2026-09-08。性质：只读架构审计；没有修改框架仓库、分支或依赖。本文件承接 [TrueLicense 4.x 迁移指南](truelicense4.md)。

## 结论矩阵

| 框架仓库/维护线 | 当前许可证实现 | 旧1.33残留 | 新4.x API适配 | 结论 |
|---|---|---|---|---|
| ddd4j-boot 2.3–2.7（JDK8/ddd4j1） | ddd4j-auth-license | core+xml直接依赖及BOM管理 | 未透传signatureAlgorithm | 阻塞消费迁移 |
| ddd4j-boot 3.0–3.5（JDK17/ddd4j2） | ddd4j-auth-license | 同上 | 同上 | 阻塞消费迁移 |
| ddd4j-boot 4.0–4.1（JDK21/ddd4j3） | ddd4j-auth-license | 同上 | 同上 | 阻塞消费迁移 |
| ddd4j-quarkus feature/3.3.x、feature/4.0.x | ddd4j-extension-license | 无用core1.33直接依赖及BOM管理 | 不应切换到auth-license | 删除陈旧依赖并回归 |
| ddd4j-javalin feature/6.7.x、7.1.x、7.2.x | ddd4j-extension-license | 无 | 与TrueLicense4无关 | 当前维护线无需变更 |
| ddd4j-cloud 2020.0.x | 未发现许可证集成 | 无匹配 | 无 | 无直接变更；受Boot传递边界影响 |

这里的“阻塞”只表示框架消费侧尚未闭合，不推翻三条ddd4j底座中已完成的4.1.4迁移。

## Boot：13条维护线是同一问题

config/consistency/ddd4j-boot-build-matrix.tsv定义13条线：2.3–2.7对应JDK8/ddd4j1，3.0–3.5对应JDK17/ddd4j2，4.0–4.1对应JDK21/ddd4j3。对这些本地branch执行只读git grep，每条线都同时包含：

- ddd4j-boot-auth-license依赖io.ddd4j:ddd4j-auth-license；
- 同一叶模块重复声明de.schlichtherle.truelicense:truelicense-core和truelicense-xml；
- ddd4j-boot-dependencies把两个旧坐标固定为1.33。

13条线的DefaultLicenseAutoConfiguration.java SHA-256均为be9c314bd941ab4a40a0450d53fad2c4ffdbabe09c75917c61b46a45d68e3ca4。它已通过Binder把LicenseProperties绑定到POJO，但创建LicenseVerify仍调用五参数构造器，因此新字段signatureAlgorithm即使绑定也不会透传，始终回落到历史默认SHA1withDSA。

当前2.7.x叶模块实际runtime dependency tree为：

    io.ddd4j.boot:ddd4j-boot-auth-license:2.7.x.20260630-SNAPSHOT
    +- io.ddd4j:ddd4j-auth-license:1.0.x.20260630-SNAPSHOT
    +- de.schlichtherle.truelicense:truelicense-core:1.33
    \- de.schlichtherle.truelicense:truelicense-xml:1.33

该结果来自JDK8/Maven3，以叶POM运行，BUILD SUCCESS。根聚合器当前另因用户工作区中tio-core版本管理缺失而模型失败；那是独立脏改红项，不能用来解释或否定叶模块的旧许可证依赖事实。

Boot最小闭合方案须同步应用13条维护线：

1. 先确保对应ddd4j1/2/3的dependencies、parent、auth聚合POM和auth-license制品都已发布/可消费；只发布叶JAR不足以证明模型可解析。
2. 从Boot叶模块删除旧core/xml直接依赖，从Boot dependencies删除旧版本属性和management条目。
3. 自动配置改为六参构造，并从LicenseProperties.getSignatureAlgorithm()透传。默认SHA1保持旧证书行为；显式SHA256用于新证书。
4. 增加Binder真实属性测试：默认值、license.signature-algorithm=SHA256withDSA透传、空白值失败；高层安装行为仍由底座测试负责。
5. 每条线用自己的JDK/Maven验证runtime tree无旧group、自动配置测试、完整模块回归和维护矩阵门禁。不能只改2.7.x后声称13线完成。

## Quarkus：无用依赖，不是TrueLicense实现

feature/3.3.x与feature/4.0.x的Ddd4jLicenseQuarkusConfig源码SHA均为c119f811969b54c519cbc3bc1ea3faaf9feeff34e2711407ac2dbe945639f2d9。配置和POM都使用io.ddd4j:ddd4j-extension-license，这是独立签名协议，不依赖auth-license的TrueLicense引擎。

但两条线的Quarkus auth-license叶POM又直接声明旧truelicense-core，对应dependencies BOM也管理1.33。当前feature/4.0.x、JDK21/Maven4叶模块runtime tree实际为：

    io.ddd4j.quarkus:ddd4j-quarkus-auth-license:4.0.x.20260630-SNAPSHOT
    +- io.ddd4j:ddd4j-extension-license:3.0.x.20260730-SNAPSHOT
    \- de.schlichtherle.truelicense:truelicense-core:1.33
       \- de.schlichtherle.truelicense:truelicense-xml:1.33

正确动作是删除叶模块旧依赖和BOM旧管理项，并运行已有LicenseEnabledEndToEndQuarkusTest及runtime tree检查；不应把Quarkus适配器改为auth-license，也不需要增加TrueLicense的signatureAlgorithm配置。该依赖树同时出现阿里云metadata RFC9457告警和大量Maven4模型告警，但只读命令退出0；这些是独立发布/模型治理问题。

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

- 本审计没有修改外部仓库；Boot未提交的t-io变更、Quarkus临时目录和Cloud现有脏改均保留。
- ddd4j三线迁移代码已被其他操作纳入当前commit，但最终指南状态仍有工作区修改；本任务没有执行提交、推送或发布。
- 当前不能声称“框架组合已完全迁移TrueLicense4”或“整体生产就绪”。
- 完成顺序应为：底座制品可消费证明 → Boot13线移除旧库并透传算法 → Quarkus2线删除无用库 → Javalin/Cloud无变更验证 → clean-cache消费者验证。
