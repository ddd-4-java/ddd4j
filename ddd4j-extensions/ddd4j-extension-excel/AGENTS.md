# ddd4j-extension-excel

Excel 工作簿转换、导入导出与模板流。

## 实现与调用

从 [ExcelKit](src/main/java/io/ddd4j/extension/excel/ExcelKit.java)、[AlternatingRowStyleStrategy](src/main/java/io/ddd4j/extension/excel/style/AlternatingRowStyleStrategy.java)、[BatchReadListener](src/main/java/io/ddd4j/extension/excel/importer/BatchReadListener.java) 进入。

修改 `ExcelKit` 时，核对公开入口 `export`、`exportMultiSheet`、`exportError`、`exportEmptyTemplate` 的调用方。

修改 `AlternatingRowStyleStrategy` 时，核对公开入口 `afterCellDispose` 的调用方。

## 回归

- [ExcelKitTest](src/test/java/io/ddd4j/extension/excel/ExcelKitTest.java) 中可从 `export_then_read_should_return_same_data`、`export_with_options_should_apply_freeze_and_zebra` 定位断言。

- [ConvertersTest](src/test/java/io/ddd4j/extension/excel/convert/ConvertersTest.java) 中可从 `localDateConverter_should_roundtrip_iso_format`、`localDateConverter_should_support_custom_pattern` 定位断言。

- [ImportResultTest](src/test/java/io/ddd4j/extension/excel/importer/ImportResultTest.java) 中可从 `of_should_return_immutable_view`、`hasErrors_empty_should_be_false` 定位断言。

```sh
./mvnw -pl ddd4j-extensions/ddd4j-extension-excel -am -Dtest=ExcelKitTest,ConvertersTest,ImportResultTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-extensions/ddd4j-extension-excel -am verify
```
