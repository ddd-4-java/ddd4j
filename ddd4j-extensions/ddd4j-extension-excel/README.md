# ddd4j-extension-excel

> ddd4j 的 Excel 通用扩展，基于 [Alibaba EasyExcel 4.x](https://github.com/alibaba/easyexcel) 的门面增强。

把 EasyExcel 的链式 API 翻译成意图明确的命名，业务侧不再出现 `.registerWriteHandler(...)` 这种细节；同时**直接复用 easyexcel 原生注解**（`@ExcelProperty` / `@ExcelIgnore` / `@ColumnWidth` / `@HeadStyle` / `@DateTimeFormat` / `@NumberFormat` / `@ContentLoopMerge`），不引入新的注解体系。

## 设计原则

1. **门面增强，不是注解替代**：easyexcel 的注解体系已经很完善，再封装一层只会增加学习成本。
2. **错误不抛到调用方**：导入错误统一通过 `ImportResult.getErrors()` 收集，参考社区共识（Yudao / 灯灯 / RuoYi）。
3. **流式优先**：大数据量用 `ReadListener` 流式读取，避免 OOM。
4. **样式对象缓存**：所有样式策略在构造期一次创建，避免触发 easyexcel 提示的"6W 样式上限"。
5. **不依赖 Spring**：参考兄弟模块（jackson/monitor）的项目惯例，扩展模块本身不引入 Spring AutoConfiguration，由上层 web 模块装配。

## Quick Start

### 1. 一行导出

```java
byte[] bytes = ExcelKit.export(OrderVO.class, orderService.listAll());
```

### 2. Web 下载（一行）

```java
@GetMapping("/orders/export")
public void export(HttpServletResponse response) {
    ExcelKit.download(response, "订单.xlsx", OrderVO.class, orderService.listAll());
}
```

### 3. 导入 + 错误回传（不抛异常）

```java
@PostMapping("/orders/import")
public Result<ImportResult<OrderVO>> importOrders(@RequestParam MultipartFile file) {
    try (InputStream in = file.getInputStream()) {
        ImportResult<OrderVO> result = ExcelKit.importExcel(in, OrderVO.class);
        return Result.ok(result);
    }
}
```

### 4. 大数据量批量入库

```java
ImportResult<OrderVO> result = ExcelKit.importExcel(
    file.getInputStream(), OrderVO.class,
    new BatchReadListener<>(1000, orderService::saveBatch)
);
```

### 5. 模板填充（工资条 / 合同 / 对账单）

```java
byte[] filled = ExcelKit.fill(
    getClass().getResourceAsStream("/templates/contract.xlsx"),
    Map.of("contractNo", "HT-001", "partyA", "甲方")
);
```

模板语法：`{var}` 普通变量，`{.}` 列表占位，`{data1.}` 带前缀多列表，`\{ \}` 转义字面量。

### 6. 自定义样式（声明式）

```java
WriteOptions options = WriteOptions.defaults();
options.setSheetName("订单");
options.setFreezeHeader(true);          // 冻结表头
options.setAlternatingRow(true);        // 斑马线
options.setStyleTemplate(ExcelStyleTemplate.FINANCE);  // 财务样式
options.addHandler(new CommentWriteHandler());  // 用户扩展点

byte[] bytes = ExcelKit.export(OrderVO.class, data, options);
```

## API 总览

### `ExcelKit` 顶层门面

| 类别 | 方法 | 说明 |
|------|------|------|
| 导出 | `export(head, data)` / `export(head, data, options)` | 单 sheet 同步导出 |
| 导出 | `exportMultiSheet(headMap, dataMap)` | 多 sheet |
| 错误 | `exportError(throwable)` / `exportError(message)` | 错误信息单页 |
| 错误 | `exportEmptyTemplate(head)` | 仅表头空模板 |
| 导入 | `importExcel(in, head)` / `importExcel(in, head, listener)` | 错误自动收集 / 自定义 listener |
| 导入 | `readAll(in, head)` | 同步全量（小数据量语法糖） |
| 填充 | `fill(template, vars)` / `fillList(template, list)` / `fillComposite(...)` | 模板填充 |
| Web | `download(response, filename, head, data)` / `download(response, filename, bytes)` | HTTP 下载 |
| Web | `upload(file, head)` / `upload(file, head, listener)` | HTTP 上传 |

### 监听器

| 类 | 用途 |
|------|------|
| `ErrorCollectingReadListener<T>` | 默认 listener，错误自动收集到 `ImportResult` |
| `BatchReadListener<T>` | 流式分批入库（每 N 行回调 Consumer） |

### Converter SPI（基于 `com.alibaba.excel.converters.Converter`）

| 类 | 用途 |
|------|------|
| `LocalDateConverter` | `LocalDate ↔ String`（默认 `yyyy-MM-dd`） |
| `LocalDateTimeConverter` | `LocalDateTime ↔ String`（默认 `yyyy-MM-dd HH:mm:ss`） |
| `EnumNameConverter<E>` | 通用枚举转换（按 `name()`，可继承自定义 label） |
| `BigDecimalStringConverter` | `BigDecimal ↔ String`（默认 `#,##0.00`，支持千分位） |

字段级使用：`@ExcelProperty(value="日期", converter=LocalDateConverter.class)`。
全局使用：`EasyExcel.read(in, head).registerConverter(new LocalDateConverter())...`。

### 样式策略

| 类 | 用途 |
|------|------|
| `ExcelStyleTemplate` | 预设样式枚举（DEFAULT/LIST/FINANCE/MINIMAL/ZEBRA） |
| `DefaultCellStyleStrategy` | 修复 bug 的默认样式（修复原 `CellStyleStrategy` 的 `columnIndexes.get(0)` 越界） |
| `DefaultColumnWidthStyleStrategy` | 自动列宽（基于 easyexcel `LongestMatchColumnWidthStyleStrategy`） |
| `DefaultRowHeightStyleStrategy` | 表头行高 |
| `FreezePaneStyleStrategy` | 冻结表头 |
| `AlternatingRowStyleStrategy` | 斑马线（偶数行底色） |

## 配置

application.yml 示例：

```yaml
ddd4j:
  excel:
    batch-size: 1000
    max-upload-mb: 50
    charset: UTF-8
    style:
      default-border: true
      auto-size-column: true
      header-row-height: 600
```

绑定到 `ExcelProperties`（Spring 环境下加 `@ConfigurationProperties(prefix = "ddd4j.excel")`）。

## 为什么不直接用 EasyExcel？

1. **API 翻译**：把 `.registerWriteHandler(...).sheet().doWrite()` 翻译成 `ExcelKit.export(head, data)`，意图明确。
2. **错误模型**：默认错误收集，避免单行失败导致整体中断。
3. **样式收敛**：样式策略一次装配，调用点 DRY。
4. **Web 集成**：文件名编码、Content-Type、Content-Disposition 一站封装。
5. **可插拔**：未来如需切换 fastexcel/poi-tl，业务侧 `ExcelKit` 入口不变。

## 测试覆盖

```
Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
```

- `ExcelKitTest` — 端到端 export → read 回环（6 用例）
- `ConvertersTest` — 4 个 Converter 双向测试（7 用例）
- `ExcelHttpKitTest` — Web 下载/校验（6 用例）
- `ImportResultTest` — 导入结果不可变 + 批量 listener（8 用例）

## 依赖

| 依赖 | 用途 |
|------|------|
| `com.alibaba:easyexcel:4.0.3` | 底层引擎（注解 / EasyExcel / Converter / WriteHandler） |
| `io.ddd4j:ddd4j-core` | `BizRuntimeException` / `I18nKit` |
| `org.apache.commons:commons-lang3` | `ExceptionUtils` |
| `commons-io:commons-io` | 流处理 |
| `org.springframework:spring-web` | `MultipartFile`（仅 `ExcelHttpKit.upload`） |

## License

Apache License 2.0
