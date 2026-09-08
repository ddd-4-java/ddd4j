# ddd4j-cloud 异常契约兼容审计

日期：2026-09-08。性质：只读架构审计和临时编译/运行探针；没有修改 ddd4j-cloud、cloud-agents 或框架仓库。

## 结论

Cloud 当前引用的 io.ddd4j.boot.core.ApiCode、CustomApiCode、BizCheckedException、BizRuntimeException 在 Boot 13 条维护分支和本地已构建的 Boot Core JAR 中都不存在。新底座提供同名语义类型，但包名位于 io.ddd4j.core 和 io.ddd4j.core.exception。

对两个 Cloud 异常类完整保留构造器、工厂方法和继承关系，仅把四个 import 指向新 Core 后，临时探针在三线全部编译成功：

| 类型 | 保留的公开入口 | JDK8 | JDK17 | JDK21 |
|---|---:|---:|---:|---:|
| CheckedException extends BizRuntimeException | 9构造器+2静态工厂 | 通过 | 通过 | 通过 |
| ValidateCodeException extends BizCheckedException | 9构造器 | 通过 | 通过 | 通过 |

这证明“异常对象源码适配”可行，不证明 HTTP 契约等价，也不授权直接修改 Cloud。

## 源码事实

Cloud 两类异常的原始路径：

- ddd4j-cloud-cmpt-core/.../exception/CheckedException.java
- ddd4j-cloud-cmpt-core/.../exception/ValidateCodeException.java

新 Core 的 BizCheckedException/BizRuntimeException具备它们调用的全部构造器，且三条 ddd4j 线的新 API 相同。原因链构造器已保留 cause。

Cloud 自身 R.failed(CheckedException) 对空 code 输出 -1；消息为空时输出“服务器异常！”。新 DefaultWebExceptionTranslator 的规则不同：

- BizRuntimeException code位于400–599时：HTTP status等于code；
- 业务码超出该范围时：HTTP status=500，但响应code保留业务码；
- code为空时：HTTP status=500、响应code=500；
- IllegalStateException：HTTP status=409。

临时运行探针三线输出完全一致：

    http-code:status=400,code=400,message=bad
    business-code:status=500,code=400100,message=invalid
    message-only:status=500,code=500,message=message
    illegal-state:status=409,code=409,message=conflict

因此不能把 import 编译成功当成 API 响应兼容。尤其是 Cloud 的业务码、空码和 IllegalStateException 状态需要应用级明确策略。

## 调用面

当前 Cloud 中 CheckedException 被邮件、Feign decoder、数据源、数据权限、WebUtils 和 AssertUtil 使用。ValidateCodeException当前只定义了构造器，未在受限搜索中发现业务调用。WebMVC/WebFlux安全与MyBatis处理器仍使用 io.hiwepy.boot.api.ApiCode，并普遍直接构造 ResponseEntity；这些是另一套响应协议，不能与新 Core ApiCode机械合并。

cloud-agents当前没有直接导入上述Cloud异常或旧ApiCode，但它依赖Cloud/企业父链时可能通过框架全局异常处理间接受影响。没有真实组合启动和相同请求差分前，不能声称无影响。

## 迁移策略

1. Cloud Core两类异常可先做四个import迁移，保持文件、类型、构造器、参数和静态工厂不变，并用三线编译契约锁定。
2. 保留Cloud自己的响应适配层作为兼容策略，不让DefaultWebExceptionTranslator自动接管历史响应。
3. 建立字面输入矩阵：空code、400、401、403、409、500、400100、i18n参数、cause；分别断言异常字段、HTTP状态和JSON字段。
4. 明确IllegalStateException在Cloud应用中保留400还是接受新默认409；这是业务兼容决策，不能由框架默认值替代。
5. WebMVC与WebFlux必须使用同一输入矩阵验证；已有安全/MyBatis专用处理器优先级也要纳入。
6. 完成Cloud实际依赖坐标和自动装配修复后，再做Boot真实入口测试；Javalin/Quarkus应验证同一业务错误模型，但不要求复制Spring的处理器结构。

## 证据边界

- 新 ddd4j 使用同步后的 CodeGraph读取 ApiCode、CustomApiCode、两种Biz异常与DefaultWebExceptionTranslator。
- ddd4j-cloud有CodeGraph索引，读取了两个Cloud异常的完整当前源码。
- 旧BMGW ddd4j和cloud-agents没有CodeGraph索引，遵循项目指令未初始化；受限文本搜索未发现直接异常类型匹配。
- 临时源位于 /tmp/ddd4j-cloud-exception-probe.p5MtZA，仅用于编译与运行，不属于交付源码。
- 当前没有修改Cloud、没有启动真实WebMVC/WebFlux应用，也没有提交、推送或发布。
