# ddd4j-data-mybatis

原生 MyBatis 的仓储、租户上下文与 SQL 观察。

沿 Configurer 安装的处理器核对最终 SQL 与字段读写。嵌套租户作用域退出时要恢复外层值并保留已有请求数据；字段写入需加密，读到无效签名时需拒绝。涉及锁定读的改动另用交易 Mapper 的真实 MySQL 联表语句验证。

实现入口见 [Ddd4jMybatisConfigurer](src/main/java/io/ddd4j/data/mybatis/config/Ddd4jMybatisConfigurer.java)、[Ddd4jTenantContext](src/main/java/io/ddd4j/data/mybatis/context/Ddd4jTenantContext.java)。

## 回归

优先运行 [Ddd4jMybatisConfigurerEncryptionTest](src/test/java/io/ddd4j/data/mybatis/config/Ddd4jMybatisConfigurerEncryptionTest.java)、[Ddd4jTenantRequestContextContractTest](src/test/java/io/ddd4j/data/mybatis/context/Ddd4jTenantRequestContextContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-mybatis -am -Dtest=Ddd4jMybatisConfigurerEncryptionTest,Ddd4jTenantRequestContextContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-mybatis -am verify
```
