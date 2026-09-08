# Shiro 正式认证路径审计

2026-09-08：只读诊断及隔离探针完成，未修改生产、sample 或测试源码；不构成生产验收。

## 源码证据

- 正式 JavalinShiroApplication:101 注册 ShiroSubjectProvider；测试 TestSupport:82 注册 WorkingSubjectProvider。
- AuthenticationController:79–81 在 RBAC 校验后仅设置 loginId/principal/timeout，没有 extra.credential。
- ShiroSubject.login:348–350 读取 extra.credential，缺失时传空字符串给 Realm；WorkingShiroSubject.login 则从测试用户仓储按 loginId 回读密码，掩盖正式装配契约缺口。
- WorkingShiroSubject 注释称父类“把 principal 当密码、不从 Session 读 principal”，但当前正式实现已读 credential 和 SESSION_KEY_PRINCIPAL；旧注释不能作为当前缺陷证明。
- ShiroSubject.verify:124 和 getPrincipalByToken:118 都返回当前 getPrincipal，没有验证传入 token。

源码位于 ddd4j-auth/ddd4j-auth-shiro/src/main/java/io/ddd4j/auth/shiro/subject/ShiroSubject.java，以及 ddd4j-samples/ddd4j-sample-javalin-shiro 的 main/test 源目录。

## 三线隔离运行

合成账号、内存 Realm、独立 JVM，无真实账号或外部服务。JDK8/17/21 分别从各线最终 full 的 GoodsResourceTest Surefire XML 中取得 java.class.path。ProtectionDomain 确认正式类来自对应仓库 target/classes、替代类来自 target/test-classes，而非旧快照 JAR。各线编译和运行均退出0。

| 操作 | 三线结果 |
|---|---|
| RBAC 预校验 | PASS |
| 正式实现 + 当前控制器形状请求（无 credential） | NotLoggedInException |
| 测试替代实现 + 相同请求 | PASS |
| 正式实现 + 显式合成 credential | PASS |
| 已认证线程 verify 无关 token | 返回非空当前主体 |

完整输出：

```text
LINE=1 COMPILE_EXIT=0

PRODUCTION_CLASS_SOURCE=file:/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-auth/ddd4j-auth-shiro/target/classes/
FIXTURE_CLASS_SOURCE=file:/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-samples/ddd4j-sample-javalin-shiro/target/test-classes/
RBAC_PREAUTH=PASS
PRODUCTION_WITH_CONTROLLER_REQUEST=NotLoggedInException
TEST_REPLACEMENT_WITH_SAME_REQUEST=PASS
PRODUCTION_WITH_EXPLICIT_CREDENTIAL=PASS
UNRELATED_TOKEN_RETURNS_CURRENT_PRINCIPAL=true

PROBE_EXIT=0
LINE=2 COMPILE_EXIT=0

PRODUCTION_CLASS_SOURCE=file:/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j-v2.0.x/ddd4j-auth/ddd4j-auth-shiro/target/classes/
FIXTURE_CLASS_SOURCE=file:/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j-v2.0.x/ddd4j-samples/ddd4j-sample-javalin-shiro/target/test-classes/
RBAC_PREAUTH=PASS
PRODUCTION_WITH_CONTROLLER_REQUEST=NotLoggedInException
TEST_REPLACEMENT_WITH_SAME_REQUEST=PASS
PRODUCTION_WITH_EXPLICIT_CREDENTIAL=PASS
UNRELATED_TOKEN_RETURNS_CURRENT_PRINCIPAL=true

PROBE_EXIT=0
LINE=3 COMPILE_EXIT=0

PRODUCTION_CLASS_SOURCE=file:/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j-v3.0.x/ddd4j-auth/ddd4j-auth-shiro/target/classes/
FIXTURE_CLASS_SOURCE=file:/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j-v3.0.x/ddd4j-samples/ddd4j-sample-javalin-shiro/target/test-classes/
RBAC_PREAUTH=PASS
PRODUCTION_WITH_CONTROLLER_REQUEST=NotLoggedInException
TEST_REPLACEMENT_WITH_SAME_REQUEST=PASS
PRODUCTION_WITH_EXPLICIT_CREDENTIAL=PASS
UNRELATED_TOKEN_RETURNS_CURRENT_PRINCIPAL=true

PROBE_EXIT=0
```

## 验收边界

这证明认证 SPI 与样例测试的覆盖缺口，不是 HTTP 404/EOF 的根因证明。无关 token 返回当前主体是敏感契约风险，尚未证明实际部署可被远程利用。

后续需确认正式凭据传递契约，测试使用正式 provider，并补无 token、错误/过期/退出后 token、其他会话 token、线程复用与 finally 清理的真实请求负例。不得把凭据写入日志、响应或长期 principal/profile；不能继续从用户仓储回读密码来掩盖装配问题。main 和 test 的 Session/Subject 恢复也需对齐。

本轮不更改认证语义、不移除替代实现、不提交/推送/发布。认证修复需要独立确认范围与安全回归；整体替代目标保持未完成。

## 完整探针

临时源文件 /tmp/ddd4j-shiro-probe.S8u8aE/ShiroProductionProbe.java，完整源码如下。以对应 JDK javac -proc:none -cp <Surefire java.class.path> 编译，再以 java -cp <探针目录>:<该classpath> ShiroProductionProbe 运行；仅用于隔离合成账号。

```java
import io.ddd4j.auth.shiro.subject.ShiroSubject;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.sample.javalin.shiro.WorkingShiroSubject;
import io.ddd4j.sample.javalin.shiro.config.AuthConfig;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryPermissionRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryRoleRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryUserRepository;
import io.ddd4j.sample.javalin.shiro.rbac.service.RbacService;
import java.util.Collections;
import java.util.Objects;
import org.apache.shiro.util.ThreadContext;

public final class ShiroProductionProbe {
    public static void main(String[] args) {
        System.out.println("PRODUCTION_CLASS_SOURCE=" + ShiroSubject.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("FIXTURE_CLASS_SOURCE=" + WorkingShiroSubject.class.getProtectionDomain().getCodeSource().getLocation());
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryRoleRepository roles = new InMemoryRoleRepository();
        RbacService service = new RbacService(users, roles, new InMemoryPermissionRepository());
        service.createUser("isolated-probe", "synthetic-probe-only", "probe", Collections.emptySet(), Collections.emptySet());
        AuthConfig.initShiro(users, roles, service);
        try {
            service.authenticate("isolated-probe", "synthetic-probe-only");
            System.out.println("RBAC_PREAUTH=PASS");
            ShiroSubject production = new ShiroSubject();
            boolean productionRejected = false;
            try {
                production.login(request());
            } catch (RuntimeException failure) {
                productionRejected = true;
                System.out.println("PRODUCTION_WITH_CONTROLLER_REQUEST=" + failure.getClass().getSimpleName());
            }
            if (!productionRejected) throw new AssertionError("Expected missing credential rejection");
            ThreadContext.remove();
            String fixtureToken = new WorkingShiroSubject(users).login(request());
            if (Objects.isNull(fixtureToken)) throw new AssertionError("Fixture login failed");
            System.out.println("TEST_REPLACEMENT_WITH_SAME_REQUEST=PASS");
            new WorkingShiroSubject(users).logout();
            ThreadContext.remove();
            AuthRequest supplied = request();
            supplied.setExtra(Collections.<String, Object>singletonMap("credential", "synthetic-probe-only"));
            production.login(supplied);
            System.out.println("PRODUCTION_WITH_EXPLICIT_CREDENTIAL=PASS");
            System.out.println("UNRELATED_TOKEN_RETURNS_CURRENT_PRINCIPAL=" + Objects.nonNull(production.verify("not-a-session-token")));
            production.logout();
        } finally {
            ThreadContext.remove();
        }
    }
    private static AuthRequest request() {
        AuthRequest request = AuthRequest.of("isolated-probe").setTimeout(7200);
        request.setPrincipal(new AuthPrincipal().setLoginId("isolated-probe").setUserId("isolated-probe"));
        return request;
    }
}
```

