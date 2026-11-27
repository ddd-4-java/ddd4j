# ddd4j-bom

## 发布框架工件

消费方在 dependencyManagement 中以 import 方式使用本 POM；它管理 io.ddd4j 工件版本。增加模块前，先在根 reactor 注册，再决定是否进入对外 BOM。示例和内部 testkit 的 reactor 身份不能作为导出的依据。

配置入口为 [pom.xml](pom.xml)。

## 验证

检查现有条目的 groupId、artifactId 和 ${revision}。在消费工程中省略目标工件版本并执行 dependency:tree，核对解析出的本线工件。

```sh
bash scripts/check-bom-alignment.sh
```
