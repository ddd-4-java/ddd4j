# Task 6.3+6.4 Report — ddd4j-data-cqrs-spring + ddd4j-data-cqrs-quarkus

## Status: DONE + Fix Round 1 DONE（三 commit，全门禁绿）

## Commits
- `77dcd90b` feat(data): ddd4j-data-cqrs-spring——Spring 适配（@Component + @Transactional）
  （含 ddd4j-core DefaultCommandBus 去 final 的启用性变更，见偏差 1）
- `9cbed6cd` feat(data): ddd4j-data-cqrs-quarkus——Quarkus Arc 适配（@ApplicationScoped + @ActivateRequestContext）

## 交付
### A. ddd4j-data-cqrs-spring
- `pom.xml`：parent ddd4j-data；`ddd4j-data-cqrs` + `spring-context` + `spring-tx`（spring-framework-bom 6.2.19，BOM 无版本）；test `spring-boot-starter-test` 局部 `${spring-boot-it.version:3.4.4}`（仅测试栈，同 -event-store-jpa Task 4.1 决策）
- `SpringCommandBus`：`@Component` 继承 `DefaultCommandBus`；构造器 `@Autowired ApplicationContext + CommandRegistry` → `getBeansOfType(CommandExecutor)` 逐个 `registry.register` → `super(registry.executors())`；类级 `@Transactional` 包 execute；零 override、零新框架抽象（无 BeanPostProcessor，ADR-0004）；javadoc 含仅服务 Spring 系/整批拒绝装配期暴露/@ComponentScan 集成方姿势/不可 final（CGLIB）/处理器不得依赖总线
- IT（`SpringCommandBusIT`，3 用例）：①真实路由 Result.success＋载荷断言；②多类型执行器冲突 → `ApplicationContextRunner`（真实容器）`hasFailed` + rootCause ISE 含命令类型名；③未注册命令 ISE 与 core 一致。TestApp=模拟集成方（`@SpringBootConfiguration+@EnableAutoConfiguration+@ComponentScan("io.ddd4j.data.cqrs")`），handler 在 `io.ddd4j.data.cqrs.sample`（@Component + @CommandHandler）；`application.yml` 见偏差 5
- ArchUnit 3：`cqrs_spring_deps_allowlist`（io.ddd4j/java/spring/lombok）、`cqrs_spring_no_ejb`、`cqrs_spring_no_jakarta_persistence`

### B. ddd4j-data-cqrs-quarkus
- `pom.xml`：模块级 quarkus-bom 再导入（`${quarkus.version}`=3.38.2 引根 pom:55，无本地属性，沿 panache 5.1 模式）；`ddd4j-data-cqrs` + `quarkus-arc`（BOM 无版本）；test `quarkus-junit5`
- `QuarkusCommandBus`：`@ApplicationScoped` 继承 `DefaultCommandBus`；构造器 `@Inject Instance<CommandExecutor<?>>` 流式收集 → 局部 `CommandRegistry` 整批注册 → `super(registry.executors())`；类级 jakarta `@ActivateRequestContext`；javadoc 含发现等价性注记（与 getBeansOfType 等价，ArC 发现键是 bean 刻板，@CommandHandler 为声明性元数据，6.5 Micronaut 须重议）＋不引入 narayana-jta（与 panache 的 @Transactional 区分）
- IT（`QuarkusCommandBusIT`，3 用例）：真实 ArC 容器 @QuarkusTest 零配置（无 H2/数据源/tx 注解）；冲突轨以固定列表 `Instance` 测试替身（仅替发现一环，注册/快照走真实构造链）
- ArchUnit 3：`cqrs_quarkus_deps_allowlist`（io.ddd4j/java/jakarta/quarkus/lombok）、`cqrs_quarkus_no_spring`、`cqrs_quarkus_no_jakarta_persistence`

### C. 注册
ddd4j-data/pom.xml 严格字母序：cqrs → **cqrs-quarkus** → **cqrs-spring** → crypto（见偏差 6）

## 门禁（实测）
- `-pl ddd4j-data/ddd4j-data-cqrs-spring -am install`：BUILD SUCCESS（core 261 + cqrs 12 + spring 6）
- `-pl ddd4j-data/ddd4j-data-cqrs-quarkus -am install`：BUILD SUCCESS（core 261 + cqrs 12 + quarkus 6）
- 终门禁 `-pl ddd4j-data/ddd4j-data-cqrs,ddd4j-data/ddd4j-data-cqrs-spring,ddd4j-data/ddd4j-data-cqrs-quarkus,ddd4j-core -am install`：**BUILD SUCCESS**，计数 **cqrs 12 + spring 6（3 IT+3 arch）+ quarkus 6（3 IT+3 arch）+ core 261**（spring/quarkus 各超 brief 的 ≥3 下限，ArchUnit 计入）
- warning 记录：spring-testcontext 启动开销——SpringCommandBusIT 共 1.07~1.20s（两份上下文：TestApp 主轨 + 冲突 Runner 轨）；Quarkus 容器启动 **1.289~1.374s**（Quarkus 3.38.2, profile test）

## 偏差与决策（brief-correction，均已 javadoc/commit 注明）
1. **DefaultCommandBus 原为 `final`**（计划/brief 假定可继承）——commit A 去掉 `final`（继承组装点），快照语义不变，javadoc 补「收集/注册必须先于 super(...)」装配顺序约束（不可用 SmartInitializingSingleton 延迟注册——计划 6.3 的该写法因快照语义实际是空总线 bug，已弃）。
2. **适配器非 final**（brief "public final 子类"不可行）：CGLIB（@Transactional）与 ArC 客户端代理（@ApplicationScoped）均需子类化 bean 类。
3. **ArC 代理约束**：正常作用域+拦截器子类需要非私有无参构造，而 DefaultCommandBus 仅有集合构造——QuarkusCommandBus 补 protected 无参构造（空集初始化，仅供代理子类，业务代码不得调用）。
4. Quarkus 侧 CommandRegistry 为构造内局部组装（brief B.2 构造器只列 Instance；避免额外 CDI producer 类）；Spring 侧按 brief A.2 注入共享 registry Bean。
5. **"application.yml 启用 tx 注解"不存在对应开关**：@EnableTransactionManagement 由 TransactionAutoConfiguration 依 TM Bean 存在与否激活；IT 按单一引导关注点刻意无 TM（@Transactional 已声明未激活），application.yml 注释注明，事务全量验证归 -event-store-jpa 阶段 4 IT。
6. brief C 文字 "quarkus 插 spring 之后" 与其自身"字母序"要求矛盾；按 controller 指令取严格字母序（cqrs-quarkus 在 cqrs-spring 前）。
7. Spring 冲突轨不用嵌套 @Configuration（会被 TestApp 的 @ComponentScan 扫入主容器致 Bean 撞名），改 `ApplicationContextRunner.withBean`（仍为真实容器启动失败）。

---

## Fix Round 1 — Spring 方法级 @Transactional（评审 Critical #1）

Commit `743d049d` fix(data): ddd4j-data-cqrs-spring——execute() 改方法级 @Transactional + 事务生效证明测试

### 问题
类级 `@Transactional` 对继承自 `DefaultCommandBus` 的 `execute()` **不生效**：Spring 事务切面的属性查找（`AbstractFallbackTransactionAttributeSource`）只查①被调方法自身②其声明类——声明类是 ddd4j-core，本类上的类级注解对继承方法永不匹配。集成方带事务管理器时静默分发非事务（原 javadoc 的「代理生效」声明失实）。

### 修复（SpringCommandBus.java）
- `execute()` 改为**方法级 `@Transactional` 纯委托 override**（`return super.execute(command)`，零路由逻辑复制）；类级注解移除
- javadoc 重写「事务与代理」节：记录声明类查找语义为防回归知识（含历史缺陷实证注记）

### 事务生效证明（第 4 IT 用例）
- **TestApp 注册 `NoopTransactionManager`**（测试树新增）：真实 `AbstractPlatformTransactionManager` begin/commit/rollback 生命周期、无资源、不引 H2——spring-tx 6.x 已移除 `ResourcelessTransactionManager`；刻意不用 Mockito mock TM（mock 绕过 `getTransaction` 真实流程，置不了事务激活标志）。该 Bean 使 `TransactionAutoConfiguration` 激活 `@Transactional` 代理
- **`TxProbeCommand`/`TxProbeCommandHandler`**：Handler 在 `execute` 内采样 `TransactionSynchronizationManager.isActualTransactionActive()`（静态 AtomicBoolean 持有器）
- **断言双保险**：①`AopUtils.isAopProxy(bus)` 为 true；②探针采样为 true

### 回归检测实证（stash 主类改动 → 旧代码重跑 → 恢复）
| 代码 | isAopProxy | isActualTransactionActive | 用例 |
|---|---|---|---|
| 旧（类级注解） | true（代理存在！） | **false** | **FAIL**（line 110 探针断言） |
| 新（方法级 override） | true | **true** | PASS |

关键发现：**isAopProxy 单独不足以检出该缺陷**——类级注解下代理仍被创建（拦截器按方法逐次计算属性，对继承方法算出 null 即放行），但事务从不包裹；`isActualTransactionActive` 探针断言才是决定性检测。这正是用例设计的依据。

### 门禁
`-pl ddd4j-data/ddd4j-data-cqrs-spring -am install` BUILD SUCCESS：**spring 7 用例**（4 IT + 3 ArchUnit；原 3 用例不改动全保留）+ cqrs 12 + core 261 不变。

### 未改动（按评审范围）
- DefaultCommandBus（un-final 保留）；Quarkus 模块；模块 pom（`${spring-boot-it.version}` 钉版）；原 3 IT 用例
- 遗留注记（后续轮次）：①模块 pom `<description>` 仍写「类级 @Transactional 包 execute」（一句话事实失实，本轮按评审范围未动 pom）；②ddd4j-core `DefaultCommandBus` javadoc 末行「事务等横切用类级注解让容器代理处理」与本缺陷同源失实；③Quarkus 侧 request-context 生效测试（评审 Important #2，minor follow-up）
