# 框架分支统一约定

- 所有版本、修复、集成及备份分支禁止声明 Java `record` 类型，包括生产代码、测试、样例及受 Git 管理的 Java 文档片段。
- 值对象使用普通类；不可变模型使用 final 类、final 字段及显式构造器。保留既有组件名访问器、构造验证、值相等性和 JSON 契约，不为跨分支一致性升级 JDK 或依赖。
- 提交前执行 `python3 scripts/check_no_records.py`；检查忽略字符串与注释，只拒绝实际类型声明。检查器自测执行 `python3 -m unittest discover -s scripts -p test_check_no_records.py`。
- 涉及类型迁移时必须执行受影响模块构建与行为回归；源码扫描通过不代替编译、序列化或业务验证。
