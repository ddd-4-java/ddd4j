# ddd4j-ddd-rules

先确定故障来自目录诊断还是 ArchUnit 类依赖。Clean 与 COLA 各自维护规则；COLA 改动应检查规则是否实际命中目标包。

## 按变更选择模块

| 工件 | 修改范围 |
|---|---|
| [ddd4j-ddd-rules-clean](ddd4j-ddd-rules-clean/AGENTS.md) | Clean 分层的目录诊断与编译期类依赖检查 |
| [ddd4j-ddd-rules-cola](ddd4j-ddd-rules-cola/AGENTS.md) | COLA 包边界、允许的依赖及逆向依赖反例 |

## 验证范围

从仓库根目录执行整组验证时使用下列命令。只改一个实现时，进入该模块指引选择对应的行为测试。

```sh
./mvnw -pl ddd4j-ddd-rules -am verify
```
