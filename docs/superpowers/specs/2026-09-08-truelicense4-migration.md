# TrueLicense 4.x 三线迁移规格

状态：三线目标迁移、相关回归、公开 API／制品核验及最终独立审查已完成。3.0 原生 CycloneDX 问题未修复，采用与 Maven4 实际图逐项一致的报告专用替代。保留 V1 格式和历史默认算法；旧 Preferences 不自动迁移。本任务未执行提交、推送或发布；期间检测到外部提交纳入迁移代码，详见迁移指南。外部 Boot/Quarkus 仓库未在本任务中修改。

## 范围与不变量

- 根目录为 ddd4j、同级 ddd4j-v2.0.x、ddd4j-v3.0.x；JDK8/17/21，Maven3/3/4，现有revision不变。
- 三线统一global.namespace.truelicense 4.1.4（Maven Central已核实），版本只在各线ddd4j-dependencies管理；具体模块不写数字版本。
- 移除旧de.schlichtherle.truelicense 1.33 Maven坐标及旧LicenseManager/LicenseParam父类依赖；不伪造许可证白名单。
- 目标是ddd4j-auth-license及其集中依赖/许可证门禁。ddd4j-extension-license已使用独立签名实现，不因过期注释而改其协议。
- 保留现有8个生产类及模块树。三线相同新API与行为，不新增JAR、不修改Shiro/JPA/MQ、不创建worktree、不切换分支、不提交推送发布。
- 本批迁移库引擎，不强制重签已有许可证：使用4.1.4的官方truelicense-v1兼容格式实现。必须清楚披露V1线格式已弃用、保留旧算法；不把此迁移称为密码算法现代化。不得引入1.x运行依赖来实现兼容。
- 旧高层LicenseVerify五参数构造、installLicense/unInstallLicense/verify、LicenseCreator及其参数入口保持。低层旧父类/参数涉及授权的源码及二进制不兼容，提供迁移表和编译客户端证明，不复制旧GPL API类。

## 低层API设计

CustomKeyStoreParam保留五参数构造及getAlias/getStorePwd/getKeyPwd/getStream，改为独立配置类，不再继承AbstractKeyStoreParam。

真实旧夹具证明，DSA公钥证书的自签算法可为SHA256withDSA，而1.33许可证本身固定SHA1withDSA；不能依赖4.x从证书推断算法。CustomKeyStoreParam与LicenseVerify保留旧五参数构造，默认显式SHA1withDSA；分别增加末尾String signatureAlgorithm的六参数重载，显式值不允许null/空白。LicenseCreatorParam与LicenseProperties新增signatureAlgorithm并使用同一历史默认常量，配置Bean传入新重载。较强2048bitDSA的新签发/消费用例显式选择SHA256withDSA，不自动回退、不统一降级密钥。迁移文档区分库版本、文件格式和签名算法。

CustomLicenseManager改为组合4.x上下文、vendor和consumer；公开构造为：
```java
public CustomLicenseManager(String subject, CustomKeyStoreParam keyStoreParam, java.util.prefs.Preferences preferences);
public void store(global.namespace.truelicense.api.License content, java.io.File file) throws global.namespace.truelicense.api.LicenseManagementException;
public global.namespace.truelicense.api.License install(java.io.File file) throws global.namespace.truelicense.api.LicenseManagementException;
public global.namespace.truelicense.api.License verify() throws global.namespace.truelicense.api.LicenseManagementException;
public void uninstall() throws global.namespace.truelicense.api.LicenseManagementException;
```

扩展校验钩子validateCreate/validate改用4.x License/LicenseValidationException，文档说明旧create/install/verify(LicenseNotary)继承扩展方式不再二进制兼容。高层消费者不要直接依赖这些内部低层钩子。

## 验收

1. 新API客户端可在三线实际编译；4.x生成→公钥安装→验证→卸载真实运行，不mock签名。
2. 错误公钥、签名/密文篡改、错误subject、过期、未生效证书拒绝；安装失败后verify不得变为true。
3. 注意4.x consumer.install只验签、不验证日期/subject；适配器必须再verify后才算安装成功，失败清理安装状态。
4. 旧1.33生成的合成许可证以隔离夹具证明4.x消费兼容，且最终运行classpath没有旧1.x库；记录fixture来源，不输出真实私钥/密码。三线Preferences测试必须隔离，不能清理用户实际license节点。

测试隔离必须覆盖LicenseVerify内部的默认userNodeForPackage调用：使用纯test的内存PreferencesFactory并在fork启动指定，BeforeAll核对实际factory根节点后才能运行。不得仅依赖macOS上可能无效的java.util.prefs.userRoot，不能清理真实默认节点。
5. 4.x依赖树/SBOM/许可证报告确认实际resolved坐标和许可；不能只改BOM但仍解析旧库。
6. 真实extension-license回归保持通过；三线公开API/源结构比对仅允许既有JDK等例外和本批三线同步新API。
7. 当前工作区大量脏改必须保留。独立审查以修改前文件快照为基线。失败日志不覆盖，不跳过Enforcer。

## 上游依据

- https://truelicense.namespace.global/guide/introduction.html：4起Apache2、Java8。
- https://github.com/christian-schlichtherle/truelicense/releases/tag/v4.1.4
- https://repo.maven.apache.org/maven2/global/namespace/truelicense/truelicense-core/maven-metadata.xml
- 本地4.1.4官方sources.jar的V1及ConsumerLicenseManager说明：兼容V1线格式；install本身不做业务验证。
