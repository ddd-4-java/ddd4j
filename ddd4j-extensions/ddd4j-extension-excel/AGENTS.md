# ddd4j-extension-excel

Excel 工作簿转换、导入导出与模板流。

区分可重复读取的字节数组流与只能消费一次的模板流。改模板填充时检查缓冲和复用路径；改导入导出时用实际工作簿往返，核对单元格值以及冻结、样式等选项。

实现入口见 [ExcelKit](src/main/java/io/ddd4j/extension/excel/ExcelKit.java)。

## 回归

优先运行 [ExcelKitTest](src/test/java/io/ddd4j/extension/excel/ExcelKitTest.java)、[ExcelFillerTemplateStreamContractTest](src/test/java/io/ddd4j/extension/excel/fill/ExcelFillerTemplateStreamContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-extensions/ddd4j-extension-excel -am -Dtest=ExcelKitTest,ExcelFillerTemplateStreamContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-extensions/ddd4j-extension-excel -am verify
```
