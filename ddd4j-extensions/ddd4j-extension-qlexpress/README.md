# Ddd4j QL-Express Component

基于 [Alibaba QLExpress](https://github.com/alibaba/QLExpress)、[JetCache](https://github.com/alibaba/jetcache)
的动态规则引擎组件，采用DDD（领域驱动设计）架构。

>
说明：本文中部分逻辑来自 [基于SpringBoot + QLExpress打造动态规则引擎：让业务规则不再束缚代码！](https://mp.weixin.qq.com/s/h2XXYiq7Ty5-xqulFfxwug)
一文。

## 架构说明

本项目按照DDD思想进行分层：

- **Domain层**：领域模型、值对象、仓储接口、领域服务接口
- **Application层**：应用服务、DTO、用例编排
- **Infrastructure层**：QLExpress配置、仓储实现、缓存实现、技术实现
- **Interfaces层**：REST接口（可选，需要Spring Web支持）

## 使用说明

### 1. 添加依赖

确保项目中已添加以下依赖：

- `qlexpress4` - QLExpress表达式引擎
- `jetcache` - JetCache 多级缓存
- `spring-boot-starter-data-redis` - Redis缓存（可选）
- `spring-boot-starter-web` - Web支持（可选，仅在使用REST接口时需要）
- `spring-boot-starter-data-jpa` - JPA支持（可选，仅在使用JPA持久化时需要）

### 2. QLExpress版本兼容性

由于不同版本的QLExpress可能有不同的API，请根据实际使用的版本调整：

- **qlexpress4 4.0.x**: 包名通常是 `com.ql.util.express`
- 如果遇到编译错误，请检查QLExpress的实际包名和API

### 3. 自定义函数

自定义函数位于 `infrastructure.function` 包中，需要根据实际使用的QLExpress版本来实现。

### 4. 持久化实现

`RuleDefinitionRepositoryImpl` 是一个接口示例，实际使用时需要：

- 如果使用JPA：创建JPA实体并实现Repository接口
- 如果使用MyBatis：创建Mapper接口和XML
- 如果使用其他持久化方案：实现 `RuleDefinitionRepository` 接口

## 核心功能

1. **规则定义管理**：规则的CRUD操作
2. **规则执行**：根据规则编码和上下文执行规则
3. **规则验证**：验证规则表达式的语法
4. **规则缓存**：使用Redis缓存规则定义，提高性能
5. **批量执行**：支持批量执行多个规则

## API示例

### 执行规则

```java
@Autowired
private RuleEngineApplicationService ruleEngineApplicationService;

Map<String, Object> context = new HashMap<>();
context.put("orderAmount", 1000);
context.put("customerLevel", "VIP");

RuleExecutionResult result = ruleEngineApplicationService.executeRule("DISCOUNT_RULE_001", context);
```

### 验证规则

```java
RuleValidationResult validation = ruleEngineApplicationService.validateRule("if (amount > 100) { return 0.1; }");
```

## Spring管理

所有服务类通过 `ExpressAutoConfiguration` 自动配置类进行管理：

- `RuleEngineApplicationService` - 应用服务
- `RuleEngineDomainServiceImpl` - 领域服务实现
- `RedisRuleCacheService` - 缓存服务实现

## DDD设计原则

本项目遵循DDD设计原则，但不依赖外部DDD框架：

- `RuleDefinition` - 领域实体，包含业务逻辑
- `RuleId` - 值对象，不可变，通过值相等性判断
- 领域对象保持独立，不依赖技术框架

## 领域事件

项目定义了以下领域事件，用于解耦业务逻辑：

- **RuleCreatedEvent** - 规则创建事件：当规则被创建时发布
- **RuleUpdatedEvent** - 规则更新事件：当规则被更新时发布，包含变更信息
- **RuleDeletedEvent** - 规则删除事件：当规则被删除时发布

所有事件都包含：

- 规则ID和编码
- 规则名称和类型
- 事件发生时间

事件发布通过 `DomainEventPublisher` 接口进行，实现可以在基础设施层使用Spring的事件机制或消息中间件。

## 注意事项

1. Controller是可选的，只有在需要REST接口时才需要Spring Web依赖
2. 持久化实现需要根据实际使用的技术栈来调整
3. QLExpress的自定义函数需要根据实际版本来实现
4. ddd-4-java的包名可能需要根据实际版本调整

