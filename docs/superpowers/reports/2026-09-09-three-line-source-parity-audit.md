# ddd4j 1.0.x / 2.0.x / 3.0.x 严格结构、API 与逻辑一致性审计

## 结论

**FAIL。当前证据不能证明三条线逻辑完全一致，并且已经找到不符合最新约束的结构、对象、方法和参数差异。**

审计基线：

| 线 | SHA | JDK |
|---|---|---|
| 1.0.x | ec2414658d8b87b1164d6d909349890676a14050 | 8 |
| 2.0.x | 4f48862c4385cf182cf6cf9f31976b3eb4bcac3a | 17 |
| 3.0.x | 0006f19e07a058dad4b4b0196b7958b61adb4465 | 21 |

三份普通 clone 分别建立独立 CodeGraph 索引，索引均为 up to date。没有使用现有脏工作区的索引。

## 严格门禁口径

只排除 ddd4j-samples、Quarkus 和 Quarkus 专属 Panache。允许 JDK 语法、Maven 依赖、Jackson 2/3 差异。不再排除 Helidon，不再放行 Micronaut/Javalin 公共 API 差异。

每条线先执行对应 JDK 的 clean compile，然后比较：

1. Maven 模块目录；
2. 生产 Java 相对路径；
3. fresh class 拓扑；
4. javap 公开方法 descriptor 与 MethodParameters；
5. CodeGraph 显式方法签名、参数名；
6. CodeGraph 方法源码范围的归一化 token；
7. git diff --no-index 双向源码与 POM 差异；
8. 同输入可执行契约。

## 结构树

| 比较 | 模块 | 生产 Java | fresh class |
|---|---:|---:|---:|
| 1.0 ↔ 2.0 | 86 ↔ 90 | 808 ↔ 821 | 913 ↔ 924 |
| 2.0 ↔ 3.0 | 90 ↔ 90 | 821 ↔ 821 | 924 ↔ 924 |

初始严格口径下，1.0.x 缺少四个模块；结合后续 JDK 字节码证据，这些模块在本文末尾重分类为必要兼容差异：

- ddd4j-data/ddd4j-data-cqrs-helidon
- ddd4j-data/ddd4j-data-projection-helidon
- ddd4j-runtime/ddd4j-runtime-helidon
- ddd4j-web/ddd4j-web-helidon

对应缺少 13 个生产 Java 文件和 15 个编译对象。1.0.x 另有只存在于该线的 Ddd4jMicronautWebFilter.ContextPropagationFactory。

## 对象、方法、参数

2.0.x 与 3.0.x 在 Jackson/Jakarta 归一化后：

- 模块差异：0/0
- 生产路径差异：0/0
- class 差异：0/0
- 公开 JVM API 差异：0/0
- CodeGraph 公共显式签名冲突：0

1.0.x 与 2.0.x/3.0.x：

- 非允许 class 差异：1/15
- 非允许公开 JVM API 差异：19/74
- CodeGraph 明确参数冲突：Ddd4jJavalinWeb.configure(Javalin) 与 configure(JavalinConfig)
- Micronaut 入口分别为 doFilter(HttpRequest, FilterChain) 与 filter(HttpRequest, FilterContinuation, MutablePropagatedContext)
- Dropwizard 类型包从 io.dropwizard.* 迁到 io.dropwizard.core.*
- Spring 扩展类型从 org.springframework.biz.* 迁到 org.springframework.extension.*

JDK 8 缺少 java.net.http.HttpClient 构造器属于允许的 JDK 差异，按 FQCN 和 descriptor 精确分类，没有用于覆盖其他差异。

## 方法体与 Git diff

CodeGraph 方法源码范围归一化比较：

| 比较 | 共同显式方法 | 单边方法键 | 方法体 token 不同 |
|---|---:|---:|---:|
| 1.0 ↔ 2.0 | 5,846 | 422 / 32 | 231 |
| 2.0 ↔ 3.0 | 5,863 | 15 / 3 | 13 |
| 1.0 ↔ 3.0 | 5,831 | 437 / 35 | 240 |

2.0 ↔ 3.0 的 13 个方法体差异中，12 个位于 Jackson 2/3 适配；另有一个未经允许的重复 JacksonException 异常映射。

1.0 ↔ 2.0 的 231 个方法体差异包含大量 Java 8 语法降级，但也包含不能仅凭语法白名单判定等价的实现差异，例如：

- JPA/JDBI EventStore 的事务、position 分配和 row mapper 实现不同；
- Micronaut 请求上下文由 ThreadLocal/插桩变为 PropagatedContext；
- Javalin 注册入口和请求 API 不同；
- WebMVC BaseExceptionHandler 分别读取原始 remote address 和 IpKit.getRemoteAddr；
- Micronaut remote host 在一条线回退 unknown，另一条线回退 getHostString()。

完整 git diff --no-index：

| 比较 | 检查文件 | 改变文件 | 生产 Java | POM | 新增路径 |
|---|---:|---:|---:|---:|---:|
| 1.0 ↔ 2.0 | 912 | 366 | 280 | 86 | 17 |
| 2.0 ↔ 3.0 | 912 | 131 | 43 | 88 | 0 |
| 1.0 ↔ 3.0 | 912 | 387 | 296 | 91 | 17 |

这些 patch 是原始证据，未将依赖差异误报为逻辑一致。

## 行为补证边界

通过：

- 三线都在各自 JDK 下完成 fresh clean compile。
- 四类关键值对象跨 JDK 的构造参数名、公开 API、equals/hashCode/toString 输出一致。
- EventStore 16 个同输入断言三线全部通过，结果向量一致，覆盖输入事件副作用、timestamp、limit、事务参与、回滚和 position 仓储端口。

未证明：

- 其余生产对象的全部输入空间、异常、副作用、并发、资源释放和框架生命周期。
- 231/240 个方法体差异均属于允许差异。
- Helidon 缺失及 Micronaut/Javalin 方法差异符合严格结构约束。

因此值对象和 EventStore 的测试通过不能提升为“三条线逻辑完全一致”。

## 证据文件

- strict2/results.json：严格结构、class、公开 API 和 CodeGraph 签名门禁
- strict-tree-report.json：模块、生产路径、测试路径双向差异
- method-body-report.json：方法体 token 差异，可执行：
  python3 scripts/audit-three-line-method-bodies.py --roots <jdk8-root> <jdk17-root> <jdk21-root> --output <output-dir>
- codegraph-object-diff.json：CodeGraph 对象差异
- git-diff-1-2.patch、git-diff-2-3.patch、git-diff-1-3.patch：完整 Git no-index patch
- value/results.json：跨 JDK 值对象契约
- eventstore/results.json：EventStore 同输入行为
- codegraph-status-ddd4j-1/2/3.txt：独立索引状态

## JDK 8 必要差异重分类

根据补充口径，以下差异不再作为业务不一致：

- record 与 Java 8 手写 final Bean，以及由此产生的 CodeGraph 显式/隐式方法数量差异；
- List.of、Map.of、copyOf、Stream.toList、模式匹配、switch 表达式等 Java 8 等价降级；
- java.net.http.HttpClient 在 JDK 8 不存在而使用兼容 HTTP 客户端；
- Helidon、Javalin、Micronaut、Dropwizard 因可用版本的基础字节码要求不同产生的适配差异；
- javax/jakarta、Jackson 2/3 API 迁移；
- POM 和依赖版本差异。

本机实际产物字节码证明：

| 组件 | 低线版本 | 高线版本 | 基础 class major |
|---|---|---|---|
| Javalin | 4.6.8 | 7.1.0/7.2.2 | 52 / 61 |
| Micronaut | 3.10.10 | 4.10.x | 52 / 61 |
| Dropwizard | 2.1.12 | 5.0.2 | 52 / 61 |
| Helidon 使用构件 | 无 | 3.2.18 | 61 |

major 52 对应 Java 8，major 61 对应 Java 17。因此 1.0.x 不能直接使用高线框架构件；Helidon 四模块缺失属于当前组件选型下的 JDK 约束差异，而不是已证明的业务删减。

重分类后仍不能直接放行的方法体差异：

- 与 JDK 无关的客户端 IP 解析策略差异；
- Micronaut remote host 回退值不同；
- 3.0.x 重复 JacksonException 异常映射；
- EventStore 实现差异虽然 16 个共同契约通过，但并发 position、事务可见性和 persistence-context 副作用尚未得到完整差分证明；
- 其余方法体差异需要按“必要语法/依赖适配”逐项归类，不能用框架版本一次性覆盖。

因此修正后的结论是：Helidon 和框架入口差异可以作为有证据的 JDK 兼容差异；当前剩余问题集中在与 JDK 无关的行为差异及尚未分类的方法体差异。
