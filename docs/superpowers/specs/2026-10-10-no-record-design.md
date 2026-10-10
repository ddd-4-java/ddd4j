# 框架分支统一：禁止 Java record

用户明确要求所有框架分支禁止 record，并将现有声明迁移为普通类；本规格适用于 ddd4j 全部既有分支（包括 backup、集成和修复分支）。

## 类型与行为契约

- 所有受 Git 管理的 Java 生产、测试、样例源码及 Java 文档片段不得声明 record。
- 迁移为 final 普通类，保留类型/包名、泛型、接口、访问级别、嵌套类型 static 语义、组件顺序及原有方法。
- 保留 canonical 构造器和 compact 构造器的验证、参数归一化和防御性复制；保留组件名访问器 `x()`。
- 保留 equals/hashCode/toString 值语义，包括浮点 NaN/负零、数组引用比较；显式重写方法优先。
- 保留 Jackson 2/3 的 JSON 属性名称、反序列化构造器与现有注解行为，不新增框架依赖。
- Serializable 原 record 默认 UID 为 0，迁移类型显式保留此值。旧 Java 序列化字节流跨类型形态兼容不作为本次已证明结论。
- Java Record 父类及反射 isRecord/record components 元数据按用户要求移除；不伪造这些元数据。
- 没有 record 的历史分支仍写入规范与自动检查。不得借迁移升级 JDK、依赖或改写远端历史。

## 验证

源码检查必须覆盖所有既有分支；行为测试覆盖普通类形态、值语义、compact 构造器、Jackson round-trip 和既有受影响测试。Maven 结果逐分支记录；环境/历史阻塞不能宣称通过。原有格式化导致 record header 倒置的源码仅恢复声明顺序，不替换业务逻辑。

## 本分支验证记录

feature/2.0.x：JDK 17 的 Core、MQ-core、Web-core 及依赖链共 710 项测试通过。新增普通类形态、值语义、Jackson 2 round-trip、Java 序列化 canonical 校验测试。guard 及其 5 项自测通过；未执行本分支全仓运行时回归。补齐迁移受影响测试既有缺失的 JUnit 静态 import，不修改断言。
