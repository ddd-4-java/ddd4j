# ddd4j-extensions

按输入产物选择扩展：工作簿、二维码、许可证、表达式、插件、追踪或机器人通知均有独立模块。把协议字段和客户端依赖留在对应扩展，确认调用者是否真的需要传递引入。

## 按变更选择模块

| 工件 | 修改范围 |
|---|---|
| [ddd4j-extension-excel](ddd4j-extension-excel/AGENTS.md) | Excel 工作簿转换、导入导出与模板流 |
| [ddd4j-extension-license](ddd4j-extension-license/AGENTS.md) | 许可证生成、机器信息验证及缓存 |
| [ddd4j-extension-otel](ddd4j-extension-otel/AGENTS.md) | 业务操作的 span 与上下文传递 |
| [ddd4j-extension-qrcode](ddd4j-extension-qrcode/AGENTS.md) | 二维码生成、解码、批处理和模板注册 |
| [ddd4j-extension-qlexpress](ddd4j-extension-qlexpress/AGENTS.md) | QLExpress 执行选项、值转换与内建函数 |
| [ddd4j-extension-monitor](ddd4j-extension-monitor/AGENTS.md) | 钉钉、飞书与企微机器人的通知发送 |
| [ddd4j-extension-pf4j](ddd4j-extension-pf4j/AGENTS.md) | PF4J 插件扩展点、映射与 Servlet 接口 |

## 验证范围

从仓库根目录执行整组验证时使用下列命令。只改一个实现时，进入该模块指引选择对应的行为测试。

```sh
./mvnw -pl ddd4j-extensions -am verify
```
