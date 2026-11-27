# ddd4j-web-dropwizard

Dropwizard Bundle 向 Jersey 注册的请求和响应组件。

## 实现与调用

从 [Ddd4jDropwizardWebBundle](src/main/java/io/ddd4j/web/dropwizard/Ddd4jDropwizardWebBundle.java)、[Ddd4jDropwizardExceptionMapper](src/main/java/io/ddd4j/web/dropwizard/Ddd4jDropwizardExceptionMapper.java)、[Ddd4jDropwizardIllegalStateExceptionMapper](src/main/java/io/ddd4j/web/dropwizard/Ddd4jDropwizardIllegalStateExceptionMapper.java) 进入。

修改 `Ddd4jDropwizardWebBundle` 时，核对公开入口 `initialize`、`run` 的调用方。

修改 `Ddd4jDropwizardExceptionMapper` 时，核对公开入口 `toResponse` 的调用方。

## 回归

- [Ddd4jDropwizardWebBundleTest](src/test/java/io/ddd4j/web/dropwizard/Ddd4jDropwizardWebBundleTest.java) 中可从 `constructorRejectsNullResolver`、`defaultConstructorBuildsDefaultConfiguration` 定位断言。

- [Ddd4jDropwizardOtelIntegrationTest](src/test/java/io/ddd4j/web/dropwizard/Ddd4jDropwizardOtelIntegrationTest.java) 中可从 `dropwizardModuleCanUseWebOtelIntegration`、`dropwizardModuleCanUseWebOtelIntegration_withTraceparent` 定位断言。

- [Ddd4jDropwizardWebConfigurationTest](src/test/java/io/ddd4j/web/dropwizard/Ddd4jDropwizardWebConfigurationTest.java) 中可从 `defaults`、`setters` 定位断言。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-dropwizard -am -Dtest=Ddd4jDropwizardWebBundleTest,Ddd4jDropwizardOtelIntegrationTest,Ddd4jDropwizardWebConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-dropwizard -am verify
```
