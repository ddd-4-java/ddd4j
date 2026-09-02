# 02. fuin API 模式：EntityIdPath 链式实体路径

> 对应 README 索引第 02 项；只读提炼 fuin 设计，ddd4j 全部自研实现（no code reuse）。

## 来源

- 仓库：https://github.com/fuinorg/ddd-4-java
- 版本：0.7.0（本地快照：`workspace-ddd4j-boot/ddd-4-java`）
- 文件：
  - `core/src/main/java/org/fuin/ddd4j/core/EntityIdPath.java:46-266`（主源）
  - `core/src/main/java/org/fuin/ddd4j/core/EntityId.java:31-118`（标识最小契约 + 静态解析）
  - `core/src/main/java/org/fuin/ddd4j/core/AggregateRootId.java:23-25`（聚合根标识标记接口）、`IntegerEntityId.java:29-119`（整型标识抽象基类）、`StringBasedEntityType.java:32-63`（类型值对象）
  - `core/src/main/java/org/fuin/ddd4j/core/ExpectedEntityIdPathValidator.java:13-51`（路径形状校验器）、`HasEntityTypeConstant.java:22-37`（TYPE 常量约束注解）
- 关键 API：
  - `first`/`last`（EntityIdPath.java:113-127）：取路径首段（聚合根标识）/ 末段（事件源标识）。
  - `rest`/`parent`（EntityIdPath.java:134-159）：去掉首段 / 末段的子路径，单元素路径返回 `null`。
  - `asBaseType`（EntityIdPath.java:170-180）：拼成 `Type id/Type id` 稳定文本。
  - `valueOf`/`isValid`/`requireArgValid`（EntityIdPath.java:194-254）：基于 `EntityIdFactory` 的解析/校验三件套。
  - `ExpectedEntityIdPathValidator.isValid`（ExpectedEntityIdPathValidator.java:32-49）：按「期望标识类型序列」逐段校验路径形状。

## fuin 的设计

核心机制：`EntityIdPath` 是从聚合根到事件源实体的有序标识列表，构造期强校验；导航代数（first/last/parent/rest）支撑事件路由；`asTypedString` 段加 `/` 分隔构成可逆文本表示。

**1）构造期不变式——列表构造器（EntityIdPath.java:83-95）**

```java
public EntityIdPath(final List<EntityId> ids) {
    Contract.requireArgNotNull("ids", ids);
    if (ids.isEmpty()) {
        throw new ConstraintViolationException("Identifier list cannot be empty");
    }
    for (final EntityId entityId : ids) {
        if (entityId == null) {
            throw new ConstraintViolationException("Identifiers in the list cannot be null");
        }
    }
    this.entityIds = new ArrayList<>();
    this.entityIds.addAll(ids);
}
```

空路径与 null 元素构造期即 fail-fast；内部持有独立 `ArrayList`，`iterator()`（:103-105）再返回防御性拷贝。

**2）导航代数——first/last（EntityIdPath.java:113-116、:124-127）**

```java
@SuppressWarnings("unchecked")
public final <T extends EntityId> T first() {
    return (T) entityIds.get(0);
}

@SuppressWarnings("unchecked")
public final <T extends EntityId> T last() {
    return (T) entityIds.get(entityIds.size() - 1);
}
```

首段恒为聚合根标识、末段为事件源标识；`rest()`（:134-143）去掉首段、`parent()`（:150-159）去掉末段，单元素路径均返回 `null`，配套 `size()`（:166-168）。

**3）稳定文本表示——asBaseType（EntityIdPath.java:170-180）**

```java
@Override
public final String asBaseType() {
    final StringBuilder sb = new StringBuilder();
    for (final EntityId entityId : entityIds) {
        if (!sb.isEmpty()) {
            sb.append(PATH_SEPARATOR);
        }
        sb.append(entityId.asTypedString());
    }
    return sb.toString();
}
```

每段用 `EntityId.asTypedString()`（EntityId.java:52；IntegerEntityId 实现为 `entityType + " " + id`，IntegerEntityId.java:100-102），段间以 `PATH_SEPARATOR = "/"`（:54）拼接，得到 `Customer deb2317d…/Person 123`——日志、存储键、HTTP 参数可读且可被 `valueOf` 回解。

**4）解析——valueOf 与 EntityIdFactory（EntityIdPath.java:196-208）**

```java
Contract.requireArgNotNull("factory", factory);
if (str == null) {
    return null;
}
final List<String> entryList = entries(str);
if (entryList.isEmpty()) {
    throw new IllegalArgumentException("Invalid entity path: '" + str + "'");
}
final List<EntityId> ids = new ArrayList<>();
for (final String entry : entryList) {
    ids.add(EntityId.valueOf(factory, entry));
}
return new EntityIdPath(ids);
```

解析依赖外部 `EntityIdFactory` 注册表：段文本先按「首个空格」拆出类型与标识（EntityId.java:108-115），再由工厂把类型字符串映射回标识实例；`isValid`/`requireArgValid`（:218-254）复用同一拆分逻辑。

**5）声明式形状校验——ExpectedEntityIdPathValidator（ExpectedEntityIdPathValidator.java:36-47）**

校验器先比对长度（`value.size() != annotations.size()` 即失败，:36-38），再用双迭代器逐段做 `expectedIdType.isAssignableFrom(actualId.getClass())`（:39-47）；配合 `HasEntityTypeConstant`（HasEntityTypeConstant.java:22-37）强制标识类暴露 `public static EntityType TYPE` 常量（默认名 `TYPE`，:29），构成整套 jakarta.validation 生态。

## 优点（值得借鉴的）

- 构造期强校验空路径/null 元素（:84-92）：非法路径无法被构造出来，路由层不必再防御。
- first/last/parent/rest/size 完整路径代数：事件路由（first=聚合根、last=事件源）与父级导航一站式，01 篇的 `@ChildEntityLocator` 逐段下钻即建立在其上。
- `asTypedString` 段 + `/` 分隔的稳定文本（:170-180）：可读、可作存储键、且与 `valueOf` 互逆。
- isValid / requireArgValid / valueOf 三件套分离（:194-254）：boolean 校验、断言抛异常、构造转换三种姿势各得其所。
- 「期望类型序列」声明式形状校验思想（ExpectedEntityIdPathValidator.java:36-47）：把路径约束从 if-else 提升为数据。
- `iterator()` 防御性拷贝（:103-105）：迭代器删除不触及内部列表。

## 缺点（应规避的）

- 解析被 `EntityIdFactory` 注册表劫持（EntityIdPath.java:195、EntityId.java:104-116）：核心 API 依赖外部工厂做类型字符串到标识实例的映射，使用方必须先注册全部类型。
- 坏输入错误链路断裂：`EntityId.valueOf` 对无空格段静默返回 `null`（EntityId.java:109-112），到 `EntityIdPath` 构造器才抛「Identifiers cannot be null」（:88-91）——丢失出错段原文。
- `first()`/`last()` 无检查强转 `(T)`（:115、:126）：调用方类型写错时，ClassCastException 在远离现场的赋值处爆炸。
- 文档不可信：`isValid`/`requireArgValid` 的 javadoc 是从 EmailAddress 值对象复制来的「well-formed email address」（:212-216、:239-244）。
- 值对象基座绑定第三方：继承 objects4j `AbstractStringValueObject`（:46）、契约用 `Contract`/`jakarta.validation`（:20-28），UI 注解 `@Label/@ShortLabel/@Tooltip/@Prompt`（:41-45）对核心库是噪音。
- 封装不一致：`parent()` 非 final（:150）而 `first()/last()/rest()` 均 final（:114、:125、:134）；单元素路径返回 `null`（:136、:152）迫使调用方判空。

## ddd4j 自研决策

原则：**继承 + 扩展** ddd4j-core 现有 `EntityIdPath`（`io/ddd4j/core/ddd/event/EntityIdPath.java`，已与 fuin 高度同构），不重写；本篇增量是 README 索引明确的「补 validate」。

- **借鉴**：
  - first/last/rest/parent/size 导航代数——ddd4j 已实现（EntityIdPath.java:68-108），保持不动；
  - 类型化段 + `PATH_SEPARATOR` 拼接的稳定文本——ddd4j `asString()`（:115-118，`@JsonValue`）已对齐，继续作为存储/日志表示；
  - 构造期强校验非空、无 null 元素——ddd4j 构造器（:42-50）已用 `List.copyOf` + 断言对齐，不可变性更强；
  - 防御性 `iterator()` 拷贝（fuin :103-105 ↔ ddd4j :57-59）已沿用。
- **改写**：
  - 补 `static isValid(String)`/`valueOf(String)`：按 ddd4j 的 `Type:id` 冒号段格式（StringEntityId.java:48-50）解析，坏输入抛 `IllegalArgumentException` 并携带出错段原文——同时修掉 fuin 的「静默 null」与工厂依赖；
  - 单元素路径 `rest()/parent()` 返回 `null`（ddd4j :88-90、:97-99）：2.0.x 保留兼容，阶段 2 以 JSpecify `@Nullable` 显式化；
  - `first()` 强转风险：Task 2.2 路由取 `entityIdPath.first()` 时先断言 `instanceof AggregateRootId`（对应 fuin AbstractAggregateRoot.java:146-149 的 fail-fast）再转型；
  - 「期望类型序列」校验：改写为测试期断言（Task 2.3 覆盖事件路径形状），不做运行时 validation。
- **不借鉴**：
  - `EntityIdFactory` 注册表解析体系——ddd4j 核心零注册表，parse 只覆盖 `StringEntityType`/`StringEntityId` 通用形态；
  - objects4j `AbstractStringValueObject`/`Contract` + `jakarta.validation`（EntityIdPath.java:20-28、ExpectedEntityIdPathValidator.java:3-4）——违背 ddd4j-core 零第三方依赖（ADR-0002）；
  - `HasEntityTypeConstant`「TYPE 常量 + 校验注解」（HasEntityTypeConstant.java:22-37）与 `IntegerEntityId` 式每类型一抽象基类——ddd4j `EntityId` 是 3 方法最小契约（io/ddd4j/core/ddd/event/EntityId.java:8-31），推荐 record 直接实现（Task 2.1 的 `OrderId` 先例）。

## 落地计划

- [ ] Task 2.2：`AggregateRoot.apply()` 子实体路由取 `entityIdPath.first()`，断言首段为 `AggregateRootId` 后再转型（fail-fast）。
- [ ] Task 2.3：单测覆盖路径导航（first/last/rest/parent/size）、单元素路径 null 语义、首段类型断言的异常路径。
- [ ] 伴随阶段 2 落地 `EntityIdPath` 补 `static isValid(String)`/`valueOf(String)`（README 索引第 02 项的 ddd4j 落地列）：`Type:id/Type:id` 格式，坏输入抛 `IllegalArgumentException`。
- [ ] Task 3.2/3.3：`StoredEvent` 与 EventPayloadSerializer 以 `asString()` 文本为持久化/JSON 表示，读取侧回解统一走新 parse API。
- [ ] Task 1.10：ADR-0002（core 零依赖）引用本文档「不借鉴 EntityIdFactory/jakarta.validation」结论。
