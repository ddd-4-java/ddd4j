# Validation 扩展

本模块直接集成 `io.github.easy4j:validation-api-extension`，额外提供 Easy4j 没有的业务数据权限约束。
通用校验注解、文件对象、策略、结果和 SPI 均使用 Easy4j 原生 API；DDD4J 不再定义另一套名字或转调包装。

## 依赖

使用 DDD4J BOM 管理版本后，引入：

```xml
<dependency>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-extension-validation</artifactId>
</dependency>
```

`ddd4j-data-datascope` 已并入本模块，`ddd4j-web-validation` 已移除。
升级时删除这两个旧依赖，避免旧类继续留在 classpath。
文件检测依赖 本分支 Tika Microsoft 解析模块及其传递依赖，未引入全量标准解析器。
本模块不再依赖用于原包装对象的 `ddd4j-kit`。

## 直接使用组件

```java
import io.github.easy4j.validation.file.DefaultUploadFile;
import io.github.easy4j.validation.file.FileValidationPolicy;
import io.github.easy4j.validation.file.FileValidationResult;
import io.github.easy4j.validation.file.FileValidationService;

DefaultUploadFile file = new DefaultUploadFile(
        "file", originalFilename, contentType, size, inputStreamSource);
FileValidationPolicy policy = FileValidationPolicy.builder()
        .allowedExtensions("pdf", "doc", "docx", "xls", "xlsx")
        .maxSizeBytes(10L * 1024L * 1024L)
        .build();
FileValidationResult result = new FileValidationService().validate(file, policy);
```

使用 `result.isValid()`、`result.getFailure()` 和 `result.getDetectedType()` 处理结果。
业务异常由调用方根据失败原因决定，不再提供 DDD4J `validateOrThrow` 或通用异常包装。
若结果需要成为外部接口响应，由接口自己的 DTO 定义协议；不为复刻旧文件类型增加公共映射层。

通用约束直接导入 `io.github.easy4j.validation.constraints` 下的
`FileNotEmpty`、`AllowableValues`、`NumberValue`、`PhoneNumber`、`StringDateValue` 等注解。
内容规则直接实现 `io.github.easy4j.validation.provider.FileContentCheckProvider` 并通过 ServiceLoader 注册。
检测扩展直接实现 `io.github.easy4j.validation.file.FileTypeDetector`，默认使用 `TikaFileTypeDetector`。

## API 迁移

这是按用户要求删除旧名字包装的源码/API 变更，需要重新编译消费者，不提供同名兼容壳。

| 旧入口 | 直接使用的组件入口 |
|---|---|
| `ValidatableFile` | `io.github.easy4j.validation.file.UploadFile` |
| `DefaultValidatableFile` | `io.github.easy4j.validation.file.DefaultUploadFile`；首参数为表单字段名 |
| `InputStreamSource` | `io.github.easy4j.validation.file.InputStreamSource` |
| `FileValidationService` / `FileValidationPolicy` / `FileValidationResult` / `FileValidationFailure` | `io.github.easy4j.validation.file` 下对应类型 |
| `DetectedFileType` / `FileTypeDetector` | `io.github.easy4j.validation.file` 下对应类型 |
| `OfficeFileTypeDetector` | `TikaFileTypeDetector`；允许范围通过策略白名单表达 |
| `FileContentCheckProvider` | `io.github.easy4j.validation.provider.FileContentCheckProvider` |
| `FileValidationException` / `validateOrThrow` | 读取组件结果，按业务自身异常契约处理 |
| `io.ddd4j.web.validation.constraints.*` | `io.github.easy4j.validation.constraints.*` |

文件失败原因直接采用组件结果，不再由 Office 包装器转换成 `TYPE_UNDETECTABLE`。
通用约束以组件语义为准：手机号使用 `isValidNumber`，数字/手机号/日期的 null 处理遵循组件实现；
必填字段组合标准 `@NotNull` / `@NotBlank`。不再维护 DDD4J 独立版本。

## 数据权限

保留 `io.ddd4j.data.datascope` 下的 provider、validator 和注解，这是组件没有的业务扩展。
`DataScopeProvider` 需要由业务提供，并通过构造器或 `ConstraintValidatorFactory` 显式接入。
默认非空策略只是既有行为，不代表业务授权已经接入；仅在容器中注册 provider 不保证自动使用。
本次直接集成修正不改变授权策略、SQL 数据范围或 Spring auth 装配行为。

## 构件要求

本次使用的 Easy4j snapshot 包含公共文件 API。消费项目必须取得对应构件，不能使用缺少这些类型的旧缓存。
本地源码和测试通过不代表远端 snapshot 已发布。
