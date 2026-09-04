# ddd4j-extension-qlexpress

`ddd4j-extension-qlexpress` 是 QLExpress 4 的纯 Java 通用工具组件，定位与
`ddd4j-extension-monitor` 一致：提供稳定门面、执行抽象、函数扩展和安全默认值，
不承载规则 CRUD、数据库仓储、缓存、领域事件或 Web Controller。

## 能力边界

- `QLExpress`：统一门面和 Builder 入口
- `QLExpressEngine`：执行、类型化结果、无异常结果、语法检查、变量分析
- `QLExpressExecutionOptions`：超时、编译缓存、数组长度、精确计算和 trace 选项
- `NamedQLFunction`：有稳定名称的函数 SPI
- 内置函数：`contains`、`startsWith`、`endsWith`、`formatDate`
- 函数注册、替换和删除：通过 Runner 快照重建保证替换真实生效
- 默认隔离安全策略，不允许访问 Java 成员或私有成员

规则管理和 Spring Boot 自动装配位于：

```text
io.ddd4j.boot:ddd4j-boot-extension-qlexpress
```

## 快速开始

```java
import io.ddd4j.extension.qlexpress.QLExpress;
import io.ddd4j.extension.qlexpress.QLExpressEngine;

QLExpressEngine engine = QLExpress.create();

Integer amount = engine.execute(
        "price * quantity",
        Map.of("price", 20, "quantity", 3),
        Integer.class);

boolean matched = engine.execute(
        "contains(name, 'ddd4j')",
        Map.of("name", "hello ddd4j"),
        Boolean.class);
```

## 执行选项

工具组件使用面向服务端场景的安全默认值：

- 超时：3000 ms
- 编译缓存：开启
- 最大数组长度：10000
- Java 成员访问：隔离
- 私有成员访问：关闭

```java
QLExpressExecutionOptions options = QLExpressExecutionOptions.builder()
        .timeoutMillis(500)
        .cache(true)
        .maxArrayLength(1000)
        .precise(true)
        .build();

Object value = engine.execute("amount * rate", context, options);
```

## 自定义函数

```java
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

QLExpressEngine engine = QLExpress.builder()
        .function(new DoubleFunction())
        .build();
```

运行期函数变更：

```java
engine.registerFunction(function);          // 同名时返回 false
engine.registerOrReplaceFunction(function); // 原子切换 Runner 快照
engine.removeFunction("doubleValue");
```

## 目录结构

```text
io.ddd4j.extension.qlexpress
├── QLExpress.java
├── QLExpressEngine.java
├── QLExpressEngineBuilder.java
├── exception/
├── function/
├── model/
└── runtime/
```

## 不属于本模块的能力

- 规则定义及版本管理
- 规则 CRUD
- 数据库仓储
- Redis / JetCache / Spring Cache
- 规则变更事件
- Spring Boot 自动配置
- REST API、鉴权、审计、指标和链路追踪

这些能力应由具体运行时或业务适配模块组合实现，避免基础工具组件演变成一个固定业务模型。
