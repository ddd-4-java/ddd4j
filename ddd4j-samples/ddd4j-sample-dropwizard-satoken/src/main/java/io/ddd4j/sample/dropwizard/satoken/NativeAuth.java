package io.ddd4j.sample.dropwizard.satoken;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.util.ReflectUtil;
import io.ddd4j.auth.satoken.subject.SaTokenSubjectDataBridge;
import io.ddd4j.auth.satoken.subject.SaTokenSubjectProvider;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.util.SubjectKit;

import java.util.List;
import java.util.Objects;

/**
 * 单应用拥有的原生 Sa-Token 会话及权限提供器；关闭恢复前任状态。
 */
public class NativeAuth implements AutoCloseable {
    private final SaTokenConfig previousConfig = SaManager.config;
    private final cn.dev33.satoken.context.SaTokenContext previousContext = currentContext();
    private final cn.dev33.satoken.context.SaTokenContext context = new cn.dev33.satoken.context.SaTokenContextForThreadLocal();
    private final StpInterface previousPermissions = currentPermissions();
    private final SaTokenConfig config;
    private final StpInterface permissions;
    private final SubjectDataProvider previousData = SubjectKit.dataProvider;
    private final SubjectDataProvider data;
    private SaTokenDaoDefaultImpl ownedDao;

    /**
     * 初始化样例原生会话，不创建默认 DAO 的隐藏清理线程。
     */
    public NativeAuth() {
        config = new SaTokenConfig().setIsPrint(false).setDataRefreshPeriod(-1).setIsReadCookie(false);
        data = new SubjectDataProvider() {
            @Override
            public List<String> getPermissionList(AuthPrincipal principal) {
                return "alice".equals(principal.getLoginId()) ? List.of("order:read", "order:write") : List.of("order:read");
            }

            @Override
            public List<String> getRoleList(AuthPrincipal principal) {
                return List.of("sample");
            }
        };
        permissions = new SaTokenSubjectDataBridge();
        try {
            SaManager.setConfig(config);
            SaManager.setSaTokenContext(context);
            SubjectKit.setDataProvider(data);
            SaManager.setStpInterface(permissions);
            // SDK setter 会销毁当前 DAO。调用方已经注册的会话存储继续由调用方管理。
            if (Objects.isNull(currentDao())) {
                ownedDao = new SaTokenDaoDefaultImpl();
                SaManager.setSaTokenDao(ownedDao);
            }
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    // SDK getter 会分配默认组件，直接读取原始槽位以区分未初始化和外部替换。
    private static cn.dev33.satoken.context.SaTokenContext currentContext() {
        return (cn.dev33.satoken.context.SaTokenContext) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(ReflectUtil.getField(SaManager.class, "saTokenContext")));
    }

    private static SaTokenDao currentDao() {
        return (SaTokenDao) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(
                ReflectUtil.getField(SaManager.class, "saTokenDao")));
    }

    private static StpInterface currentPermissions() {
        return (StpInterface) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(
                ReflectUtil.getField(SaManager.class, "stpInterface")));
    }

    private static void restore(Runnable... components) {
        Throwable failure = null;
        for (Runnable component : components) {
            try {
                component.run();
            } catch (RuntimeException | Error cleanupFailure) {
                // SDK 在写入槽位后通知监听器。监听器失败时仍须恢复余下组件。
                if (Objects.isNull(failure)) {
                    failure = cleanupFailure;
                } else if (failure != cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
    }

    /**
     * @param username 演示账号
     * @param password 演示密码
     * @return 原生会话令牌
     */
    public String login(String username, String password) {
        if (!("alice".equals(username) || "reader".equals(username)) || !"sample-password".equals(password)) {
            throw new io.ddd4j.web.core.error.WebStatusException(401, "invalid credentials");
        }
        var principal = new AuthPrincipal().setLoginId(username).setUserId(username);
        return new SaTokenSubjectProvider().getSubject().login(AuthRequest.of(username).setPrincipal(principal));
    }

    /**
     * 关闭本实例注册的原生资源并恢复仍由本实例持有的配置。
     */
    @Override
    public synchronized void close() {
        SaTokenDao dao = ownedDao;
        ownedDao = null;
        restore(
                () -> {
                    if (Objects.nonNull(dao) && currentDao() == dao) {
                        // SDK 在解除注册时销毁 DAO。已经被替换的 DAO 已由 SDK 销毁。
                        SaManager.setSaTokenDao(null);
                    }
                },
                () -> {
                    if (currentPermissions() == permissions) {
                        SaManager.setStpInterface(previousPermissions);
                    }
                },
                () -> {
                    if (currentContext() == context) {
                        SaManager.setSaTokenContext(previousContext);
                    }
                },
                () -> {
                    if (SaManager.config == config) {
                        SaManager.config = previousConfig;
                    }
                },
                () -> {
                    if (SubjectKit.dataProvider == data) {
                        SubjectKit.setDataProvider(previousData);
                    }
                });
    }
}
