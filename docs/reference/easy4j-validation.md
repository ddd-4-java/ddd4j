# Easy4j 校验接入

通过框架父 POM 继承，或显式导入 `io.ddd4j:ddd4j-dependencies` 管理依赖版本，然后直接声明组件。

```xml
<dependency>
    <groupId>io.github.easy4j</groupId>
    <artifactId>validation-api-extension</artifactId>
</dependency>
<!-- 实际检测 Office 文件的应用额外引入 -->
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parser-microsoft-module</artifactId>
    <scope>runtime</scope>
</dependency>
```

旧的 `io.ddd4j:ddd4j-extension-validation` 已移除。对应版本线的 Easy4j SNAPSHOT 已发布到阿里云私服。

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

注解、Provider 和 Validator 统一由 Easy4j 提供，DDD4J 不再声明另一套类型：

| 旧类型（data/auth 两套包） | 上游类型 |
| --- | --- |
| `annotation.RequiresDataPermissions` | `io.github.easy4j.validation.constraints.RequiresDataPermissions` |
| `DataScopeProvider` | `io.github.easy4j.validation.provider.DataScopeProvider` |
| `RequiresDataPermissionsValidator` | `io.github.easy4j.validation.constraintvalidators.RequiresDataPermissionsValidator` |

更新 import 并重新编译消费者。普通 Bean Validation 通过现有 `ConstraintValidatorFactory` 显式传入 Provider；Spring 应用注册上游 Provider Bean 并使用原生 `LocalValidatorFactoryBean`，标准 `@Inject` 负责注入。不需要旧的 DataScopeAutoConfiguration、静态 SpringContextUtils 或重复工厂。

未配置 Provider 必须报配置错误，不能默认放行；配置后 null 仍产生约束违规。旧 `nonNullAllowed()` 和默认 Provider Bean 已删除。这是明确的授权行为变更。SQL 数据范围机制保持其独立配置。

auth 与 OTel 直接依赖上游权限契约。
