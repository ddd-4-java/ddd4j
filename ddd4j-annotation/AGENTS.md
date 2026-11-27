# ddd4j-annotation

业务构造型与 API 注解的默认值、保留策略。

维护请求幂等注解时，把已有调用方省略注解参数的写法也作为输入。改变默认值会改变已有端点；改变保留策略会影响运行时读取。新增领域标记保持纯 Java，框架对标记的解释放在适配层。

实现入口见 [ApiIdempotent](src/main/java/io/ddd4j/annotation/api/ApiIdempotent.java)、[BusinessType](src/main/java/io/ddd4j/annotation/BusinessType.java)。

## 回归

优先运行 [ApiIdempotentTest](src/test/java/io/ddd4j/annotation/api/ApiIdempotentTest.java)、[AnnotationIndependenceTest](src/test/java/io/ddd4j/annotation/arch/AnnotationIndependenceTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-annotation -am -Dtest=ApiIdempotentTest,AnnotationIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-annotation -am verify
```
