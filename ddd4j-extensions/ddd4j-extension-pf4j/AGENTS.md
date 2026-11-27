# ddd4j-extension-pf4j

PF4J 插件扩展点、映射与 Servlet 接口。

插件扩展点的 Servlet 参数是插件作者编译时可见的签名。改鉴权扩展或升级 Servlet 依赖时，先核对当前版本线命名空间，再运行扩展点签名回归；更换参数类型会影响已编译的插件。

实现入口见 [AuthcExtensionPointAdapter](src/main/java/io/ddd4j/extension/pf4j/point/authc/AuthcExtensionPointAdapter.java)。

## 回归

优先运行 [Pf4jServletContractTest](src/test/java/io/ddd4j/extension/pf4j/Pf4jServletContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-extensions/ddd4j-extension-pf4j -am -Dtest=Pf4jServletContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-extensions/ddd4j-extension-pf4j -am verify
```
