# ddd4j-extension-qrcode

二维码生成、解码、批处理和模板注册。

生成和解码应保留关联元数据。批次保持输入顺序，单项失败允许其余项成功。绑定文字时不要修改已注册的模板；用同一模板连续生成两次可观察到误修改。

实现入口见 [DefaultQrCodeService](src/main/java/io/ddd4j/extension/qrcode/DefaultQrCodeService.java)、[QrCodeTemplateRegistry](src/main/java/io/ddd4j/extension/qrcode/template/QrCodeTemplateRegistry.java)。

## 回归

优先运行 [DefaultQrCodeServiceTests](src/test/java/io/ddd4j/extension/qrcode/DefaultQrCodeServiceTests.java)、[QrCodeTemplateRegistryTests](src/test/java/io/ddd4j/extension/qrcode/template/QrCodeTemplateRegistryTests.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-extensions/ddd4j-extension-qrcode -am -Dtest=DefaultQrCodeServiceTests,QrCodeTemplateRegistryTests -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-extensions/ddd4j-extension-qrcode -am verify
```
