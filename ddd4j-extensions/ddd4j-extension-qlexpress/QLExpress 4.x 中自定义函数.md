# QLExpress 4.x 自定义函数

本模块基于 `com.alibaba:qlexpress4:4.1.2`。业务代码通过 `QLExpress` 门面注册函数；不要使用 QLExpress 3.x 的 `com.ql.util.express.ExpressRunner`、`OperatorBase` 或旧包名 `com.ql.util.express.annotation.QLFunction`。三条产品线共用下面的函数契约，JDK 和依赖版本分别由各线 POM 管理。

## 定义函数

`NamedQLFunction` 继承 QLExpress 4 的 `com.alibaba.qlexpress4.runtime.function.CustomFunction`。函数名由 `name()` 提供，参数由 `Parameters` 提供；`call` 返回可用于表达式求值的结果。以下实现与 `QLExpressEngineTest` 的已测试函数一致，语法兼容 JDK 8。

```java
import com.alibaba.qlexpress4.runtime.Parameters;
import com.alibaba.qlexpress4.runtime.QContext;
import io.ddd4j.extension.qlexpress.function.NamedQLFunction;

public final class DoubleFunction implements NamedQLFunction {
    @Override
    public String name() {
        return "doubleValue";
    }

    @Override
    public Object call(QContext context, Parameters parameters) {
        return ((Number) parameters.getValue(0)).intValue() * 2;
    }
}
```

真实业务函数应校验参数个数与类型，并明确空值和异常语义。可参考本模块的 `StartsWithFunction`、`FormatDateFunction` 和 `FunctionArguments`；上例只展示注册接口。

## 注册与执行

```java
import io.ddd4j.extension.qlexpress.QLExpress;
import io.ddd4j.extension.qlexpress.QLExpressEngine;
import java.util.Collections;

QLExpressEngine engine = QLExpress.builder()
        .function(new DoubleFunction())
        .build();

Integer result = engine.execute("doubleValue(5)",
        Collections.<String, Object>emptyMap(), Integer.class);
```

`result` 为 `10`。`QLExpress.create()` 默认注册 `contains`、`startsWith`、`endsWith`、`formatDate` 四个内置函数；Builder 的 `builtInFunctions(false)` 可关闭这些默认函数。`registerFunction` 对已存在名称返回 `false`；`registerOrReplaceFunction` 替换同名函数；`removeFunction` 删除函数。这些变更通过 Runner 快照重建生效，已有引擎的后续执行会使用新注册表。

## 直接使用 QLExpress 4 API

本模块的 `QLExpressEngine.unwrap()` 返回 `com.alibaba.qlexpress4.Express4Runner`。只有需要底层 API、且接受其版本耦合时才使用它。QLExpress 4 的方法注解位于 `com.alibaba.qlexpress4.annotation.QLFunction`；它与上面的 `NamedQLFunction` 扩展点不同，不应混用旧版示例。

Spring Boot 自动配置、规则仓储、缓存和 Web 接口位于上层适配模块，不属于此纯 Java 工具模块。完整入口和安全默认值见同目录 `README.md`；可运行的函数注册与替换契约见 `src/test/java/io/ddd4j/extension/qlexpress/QLExpressEngineTest.java`。
