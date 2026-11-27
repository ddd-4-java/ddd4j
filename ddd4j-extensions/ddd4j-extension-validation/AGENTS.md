# ddd4j-extension-validation

维护文件校验策略、类型识别与内容检查。按失败类型区分策略拒绝和检测器异常，检查提供者传入的文件与调用者收到的校验结果。

## 实现与调用

从 [FileContentCheckProvider](src/main/java/io/ddd4j/extension/validation/FileContentCheckProvider.java)、[FileValidationService](src/main/java/io/ddd4j/extension/validation/FileValidationService.java)、[DefaultValidatableFile](src/main/java/io/ddd4j/extension/validation/DefaultValidatableFile.java) 进入。

修改 `FileValidationService` 时，核对公开入口 `validate`、`validateOrThrow` 的调用方。

## 回归

- [FileValidationServiceTest](src/test/java/io/ddd4j/extension/validation/FileValidationServiceTest.java) 中可从 `shouldValidateSupportedDocumentSignatures`、`shouldRejectRenamedAndUnsupportedFiles` 定位断言。

```sh
./mvnw -pl ddd4j-extensions/ddd4j-extension-validation -am -Dtest=FileValidationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-extensions/ddd4j-extension-validation -am verify
```
