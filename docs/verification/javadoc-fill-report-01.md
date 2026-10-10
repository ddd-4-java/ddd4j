# javadoc 补全报告 batch-01

说明：状态取值 —— `已新增`=本次补写 javadoc；`已存在`=清单符号上方原本已有完整 javadoc，复核后跳过；`已存在+HAZARD已修复`=符号 javadoc 已存在且清单要求的链接改写已完成。

| 路径 | 处理符号数 | 状态 |
| --- | --- | --- |
| ddd4j-annotation/src/main/java/io/ddd4j/annotation/BusinessType.java | 10 | 已存在 |
| ddd4j-annotation/src/main/java/io/ddd4j/annotation/api/ApiIdempotentType.java | 2 | 已存在 |
| ddd4j-annotation/src/test/java/io/ddd4j/annotation/api/ApiIdempotentTest.java | 1 | 已新增 |
| ddd4j-auth/ddd4j-auth-datascope/src/main/java/io/ddd4j/auth/datascope/DataScopeProvider.java | 1 | 已存在 |
| ddd4j-auth/ddd4j-auth-license/src/main/java/io/ddd4j/auth/license/CustomKeyStoreParam.java | 1 | 已新增 |
| ddd4j-auth/ddd4j-auth-license/src/main/java/io/ddd4j/auth/license/LicenseProperties.java | 1 | 已新增 |
| ddd4j-auth/ddd4j-auth-license/src/main/java/io/ddd4j/auth/license/manager/CustomLicenseManager.java | 1 | 已新增 |
| ddd4j-auth/ddd4j-auth-license/src/test/java/io/ddd4j/auth/license/LicenseTestPreferencesFactory.java | 5 | 已新增 |
| ddd4j-auth/ddd4j-auth-license/src/test/java/io/ddd4j/auth/license/TrueLicenseMigrationTest.java | 4 | 已新增 |
| ddd4j-auth/ddd4j-auth-satoken/src/main/java/io/ddd4j/auth/satoken/SaConstants.java | 5 | 已存在 |
| ddd4j-auth/ddd4j-auth-satoken/src/main/java/io/ddd4j/auth/satoken/config/Ddd4jStpLogicJwtForSimple.java | 2 | 已新增 |
| ddd4j-auth/ddd4j-auth-satoken/src/main/java/io/ddd4j/auth/satoken/config/SaTokenAuthenticationMode.java | 2 | 已存在 |
| ddd4j-auth/ddd4j-auth-satoken/src/main/java/io/ddd4j/auth/satoken/subject/SaTokenSubject.java | 1 | 已存在 |
| ddd4j-auth/ddd4j-auth-satoken/src/main/java/io/ddd4j/auth/satoken/util/StpKit.java | 4 | 已存在 |
| ddd4j-auth/ddd4j-auth-satoken/src/test/java/io/ddd4j/auth/satoken/handler/SaInternalCheckHandlerTest.java | 2 | 已新增 |
| ddd4j-auth/ddd4j-auth-satoken/src/test/java/io/ddd4j/auth/satoken/handler/SaMixCheckLoginHandlerTest.java | 1 | 已新增 |
| ddd4j-auth/ddd4j-auth-security/src/main/java/io/ddd4j/auth/security/subject/SecuritySubject.java | 1 | 已存在 |
| ddd4j-auth/ddd4j-auth-shiro/src/main/java/io/ddd4j/auth/shiro/subject/ShiroSubject.java | 2 | 已存在+HAZARD已修复 |
| ddd4j-auth/ddd4j-auth-shiro/src/test/java/io/ddd4j/auth/shiro/subject/ShiroSubjectTest.java | 1 | 已存在 |
| ddd4j-cache/src/main/java/io/ddd4j/cache/CacheKit.java | 4 | 已存在 |
| ddd4j-cache/src/main/java/io/ddd4j/cache/jetcache/adapter/JetCacheCacheManager.java | 1 | 已存在 |
| ddd4j-cache/src/main/java/io/ddd4j/cache/subject/InMemorySubject.java | 5 | 已新增 |
| ddd4j-cache/src/test/java/io/ddd4j/cache/CacheKitTest.java | 1 | 已存在 |
| ddd4j-cache/src/test/java/io/ddd4j/cache/lettuce/LettuceCacheRedisTest.java | 1 | 已存在 |
| ddd4j-core/src/main/java/io/ddd4j/core/ApiCode.java | 20 | 已新增 |
| ddd4j-core/src/main/java/io/ddd4j/core/ApiCodeValue.java | 20 | 已存在 |
| ddd4j-core/src/main/java/io/ddd4j/core/CustomApiCode.java | 3 | 已存在 |
| ddd4j-core/src/main/java/io/ddd4j/core/HttpStatus.java | 20 | 已新增 |
| ddd4j-core/src/main/java/io/ddd4j/core/api/IR.java | 6 | 已新增 |
| ddd4j-core/src/main/java/io/ddd4j/core/api/R.java | 3 | 已新增 |
| **批次合计** | **131** | 30 个文件全部通过复查；HAZARD 1 处已修复；新增 javadoc 53 处、已存在复核 78 处 |

复查方式：脚本逐符号定位声明行，向上跳过注解/空行后确认存在以 `*/` 结尾的 javadoc 块；HAZARD 行确认原文件中不再出现 `{@link #getPrincipal()}`。全部通过。
