# ddd4j-parent

## 检查下游继承的构建模型

本 POM 继承 ddd4j-dependencies。改 dependencies 会改变子项目默认类路径；改 pluginManagement 要看消费方是否启用了对应插件。涉及 Surefire、编译器或发布插件时，分别看插件配置和已绑定的 execution。

配置入口为 [pom.xml](pom.xml)。

## 验证

再在实际继承本 parent 的工程执行 help:effective-pom。比较目标插件的 execution、phase 和最终依赖 scope，确认改动到达了预期消费方。

```sh
./mvnw -f ddd4j-parent/pom.xml help:effective-pom
```
