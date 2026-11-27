# ddd4j-data-external

地理、地区、天气与序列号服务适配。

地理查询先编码输入，再解析服务商响应。改地区查询缓存时，区分命中、服务商业务错误和 HTTP 错误：失败结果不能遮蔽下一次成功查询。用各提供商的 HTTP 夹具核对缓存期限，地址和 IP 的响应结构要分开处理。

实现入口见 [GeoBaiduTemplate](src/main/java/io/ddd4j/data/external/geo/GeoBaiduTemplate.java)。

## 回归

优先运行 [GeoBaiduHttpContractTest](src/test/java/io/ddd4j/data/external/geo/GeoBaiduHttpContractTest.java)、[BaiduRegionHttpContractTest](src/test/java/io/ddd4j/data/external/region/BaiduRegionHttpContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-external -am -Dtest=GeoBaiduHttpContractTest,BaiduRegionHttpContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-external -am verify
```
