# Shiro 登录真实 HTTP 对照

2026-09-08。只读诊断阶段，无生产/sample/POM 源码修改。承接 [正式认证路径审计](shiro-real-provider-audit.md)。

## 方法与边界

使用正式 AuthenticationController、AuthConfig、RbacService 和合成账号建立最小 Javalin 装配，绑定127.0.0.1随机端口。每种provider在独立JVM运行；请求使用新的HttpURLConnection、Connection:close并disconnect。仅provider不同：正式ShiroSubjectProvider或测试WorkingSubjectProvider。请求前后清除ThreadContext。未修改登录控制器，没有覆盖异常处理，也没有修改凭据内容来使正式路径通过。

这是实际HTTP+正式控制器的最小集成对照，不是直接启动完整JavalinShiroApplication，也不证明全应用授权、Bearer恢复、线程隔离或远程漏洞。404/EOF故障仍是另外的问题。

## 结果

| 版本线 | 正确合成密码/正式provider | 正确密码/测试替代provider | 错误密码/两种provider |
|---|---:|---:|---:|
| 1.0/JDK8 | 500 | 200 | 401 |
| 2.0/JDK17 | 500 | 200 | 401 |
| 3.0/JDK21 | 500 | 200 | 401 |

各线最终探针编译、两次运行均exit0（探针断言上述差异，不代表正式登录通过）。1.0输出：

```text
LINE=1 COMPILE_EXIT=0
MODE=production VALID_PASSWORD_HTTP=500 WRONG_PASSWORD_HTTP=401
RUN_EXIT=0
MODE=fixture VALID_PASSWORD_HTTP=200 WRONG_PASSWORD_HTTP=401
RUN_EXIT=0
```

2.0/3.0输出：

```text
LINE=2 COMPILE_EXIT=0
MODE=production VALID_PASSWORD_HTTP=500 WRONG_PASSWORD_HTTP=401
RUN_EXIT=0
MODE=fixture VALID_PASSWORD_HTTP=200 WRONG_PASSWORD_HTTP=401
RUN_EXIT=0
LINE=3 COMPILE_EXIT=0
MODE=production VALID_PASSWORD_HTTP=500 WRONG_PASSWORD_HTTP=401
RUN_EXIT=0
MODE=fixture VALID_PASSWORD_HTTP=200 WRONG_PASSWORD_HTTP=401
RUN_EXIT=0
```

最初将1.0 Javalin路由注册API用于2.0/3.0时，临时探针编译失败（before/after/post不在Javalin实例上）；未当作项目回归。根据本地TestSupport代码改为cfg.routes.before/after和cfg.routes.apiBuilder后运行成功。没有变更任何业务逻辑。

## 结论及后续验收

正确密码已在RBAC通过，但当前控制器未将credential传给正式ShiroSubject；测试替代实现自行从仓储回读密码，掩盖正式链路缺口。前一轮隔离SPI探针已验证显式提供合成credential后正式登录成功。下一步认证修复须确认安全范围，不能只稳定HTTP测试客户端就宣称可迁移。

修复验收至少包括：正式provider正确/错误密码HTTP对照、凭据不出现在日志/响应/principal中、真实token与错误/过期/退出后token、其他会话token、请求及线程边界清理。不得通过测试替代provider保留假绿。未提交、推送或发布。

## 可复现源码

classpath与前一报告一致，使用各线最终full GoodsResourceTest Surefire XML的java.class.path；对应JDK编译后运行参数production和fixture。源文件仅在/tmp/ddd4j-shiro-probe.S8u8aE。以下为1.0完整版本：

```java
import io.ddd4j.auth.shiro.subject.ShiroSubjectProvider;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.javalin.shiro.WorkingSubjectProvider;
import io.ddd4j.sample.javalin.shiro.config.AuthConfig;
import io.ddd4j.sample.javalin.shiro.rbac.controller.AuthenticationController;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryPermissionRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryRoleRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryUserRepository;
import io.ddd4j.sample.javalin.shiro.rbac.service.RbacService;
import io.javalin.Javalin;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;
import org.apache.shiro.util.ThreadContext;

public final class ShiroHttpProbe {
    public static void main(String[] args) throws Exception {
        boolean fixture = "fixture".equals(args[0]);
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryRoleRepository roles = new InMemoryRoleRepository();
        RbacService rbac = new RbacService(users, roles, new InMemoryPermissionRepository());
        rbac.createUser("isolated-probe", "synthetic-probe-only", "probe", Collections.emptySet(), Collections.emptySet());
        AuthConfig.initShiro(users, roles, rbac);
        SubjectProvider provider = fixture ? new WorkingSubjectProvider(users) : new ShiroSubjectProvider();
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider);
        SubjectKit.register(provider);
        AuthenticationController controller = new AuthenticationController(rbac);
        Javalin app = Javalin.create();
        app.before(ctx -> ThreadContext.remove());
        app.after(ctx -> ThreadContext.remove());
        app.post("/auth/login", controller::login);
        app.start("127.0.0.1", 0);
        try {
            int valid = post(app.port(), "synthetic-probe-only");
            int wrong = post(app.port(), "wrong-synthetic-value");
            System.out.println("MODE=" + args[0] + " VALID_PASSWORD_HTTP=" + valid + " WRONG_PASSWORD_HTTP=" + wrong);
            if (valid != (fixture ? 200 : 500) || wrong != 401) {
                throw new AssertionError("Unexpected HTTP result");
            }
        } finally {
            app.stop();
            ThreadContext.remove();
        }
    }
    private static int post(int port, String password) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/auth/login").openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Connection", "close");
            connection.setDoOutput(true);
            String json = "{\"loginId\":\"isolated-probe\",\"password\":\"" + password + "\"}";
            try (OutputStream output = connection.getOutputStream()) {
                output.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (Objects.nonNull(stream)) {
                try (InputStream body = stream) { while (body.read() != -1) { } }
            }
            return status;
        } finally {
            connection.disconnect();
        }
    }
}
```

2.0/3.0版本类名改为ShiroHttpProbeModern，仅将注册片段改为：

```java
Javalin app = Javalin.create(cfg -> {
    cfg.routes.before(ctx -> ThreadContext.remove());
    cfg.routes.after(ctx -> ThreadContext.remove());
    cfg.routes.apiBuilder(() -> io.javalin.apibuilder.ApiBuilder.post("/auth/login", controller::login));
});
```

