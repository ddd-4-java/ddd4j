# ddd4j-kit

Bean 转换、JSON 与基础工具。

改 Bean 拷贝要用含重复项与 null 的忽略列表验证，既有调用者会这样传参。改 JSON 日期转换要检查时区和反序列化后的值。查当前模块已用 Hutool 的实现后，再决定是否扩展工具方法。

实现入口见 [BeanKit](src/main/java/io/ddd4j/kit/lang/BeanKit.java)、[JsonKit](src/main/java/io/ddd4j/kit/lang/JsonKit.java)。

## 回归

优先运行 [BeanKitIgnoreFieldsContractTest](src/test/java/io/ddd4j/kit/lang/BeanKitIgnoreFieldsContractTest.java)、[JsonKitDateTimeContractTest](src/test/java/io/ddd4j/kit/lang/JsonKitDateTimeContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-kit -am -Dtest=BeanKitIgnoreFieldsContractTest,JsonKitDateTimeContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-kit -am verify
```
