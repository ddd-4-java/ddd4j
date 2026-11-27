# ddd4j-web-testkit

跨服务器的 HTTP 测试契约与客户端夹具。

公共 HTTP 断言通过 WebContractClient 发请求，实际服务器由各 Web 适配器提供。改响应值的相等、哈希或 JSON 表示时用本模块断言；改服务器语义则要从实际适配器运行继承的用例。数据权限 provider 缺失应拒绝访问。

实现入口见 [AbstractWebContractTest](src/main/java/io/ddd4j/web/testkit/AbstractWebContractTest.java)、[WebContractClient](src/main/java/io/ddd4j/web/testkit/WebContractClient.java)。

## 回归

优先运行 [WebContractResponseValueContractTest](src/test/java/io/ddd4j/web/testkit/WebContractResponseValueContractTest.java)、[DataPermissionSpringIntegrationTest](src/test/java/io/ddd4j/web/testkit/validation/DataPermissionSpringIntegrationTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-testkit -am -Dtest=WebContractResponseValueContractTest,DataPermissionSpringIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-testkit -am verify
```
