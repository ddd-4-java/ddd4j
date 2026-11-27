# ddd4j-data-mybatisplus

MyBatis-Plus 聚合仓储、租户拦截与结果回填。

在已有 BaseMapper 和拦截器链内维护数据访问，单表条件沿用 Lambda，复杂查询进入 XML。线程池复用时覆盖成功和异常两种退出，下一任务都应读到自己的租户。并发测试使用同一个 delegate，能捕获每次新建对象测不到的串租户问题。

实现入口见 [Ddd4jMybatisConfigurer](src/main/java/io/ddd4j/data/mybatis/config/Ddd4jMybatisConfigurer.java)、[Ddd4jTenantContext](src/main/java/io/ddd4j/data/mybatis/context/Ddd4jTenantContext.java)。

## 回归

优先运行 [Ddd4jTenantContextConcurrencyTest](src/test/java/io/ddd4j/data/mybatis/context/Ddd4jTenantContextConcurrencyTest.java)、[Ddd4jTenantRequestContextContractTest](src/test/java/io/ddd4j/data/mybatis/context/Ddd4jTenantRequestContextContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-mybatisplus -am -Dtest=Ddd4jTenantContextConcurrencyTest,Ddd4jTenantRequestContextContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-mybatisplus -am verify
```
