package io.ddd4j.sample.helidon.satoken;

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
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Objects;

/**
 * 单应用拥有的原生 Sa-Token 会话及权限提供器；关闭恢复前任状态。
 */
@ApplicationScoped
public class NativeAuth {
    /**
     * 安装示例组件前保存的 Sa-Token 配置。
     */
    private final SaTokenConfig previousConfig = SaManager.config;
    /**
     * 安装示例组件前保存的 Sa-Token 权限提供器。
     */
    private final StpInterface previousPermissions = currentPermissions();
    /**
     * 本示例安装的 Sa-Token 会话配置。
     */
    private final SaTokenConfig config;
    /**
     * 将 DDD4J 主体角色和权限数据接入 Sa-Token 的桥接组件。
     */
    private final StpInterface permissions;
    /**
     * 安装示例组件前保存的 DDD4J 主体数据提供器。
     */
    private final SubjectDataProvider previousData = SubjectKit.dataProvider;
    /**
     * 为示例账号提供权限和角色数据的 DDD4J 数据提供器。
     */
    private final SubjectDataProvider data;
    /**
     * 仅在原 DAO 为空时注册的示例会话存储。
     */
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

    /**
     * 读取 Sa-Token DAO 的原始槽位，避免创建默认会话存储。
     *
     * @return 已注册的会话存储，可以为空
     */
    private static SaTokenDao currentDao() {
        return (SaTokenDao) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(
                ReflectUtil.getField(SaManager.class, "saTokenDao")));
    }

    /**
     * 读取权限提供器的原始槽位，避免 getter 安装默认组件。
     *
     * @return 当前的权限提供器，可以为空
     */
    private static StpInterface currentPermissions() {
        return (StpInterface) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(
                ReflectUtil.getField(SaManager.class, "stpInterface")));
    }

    /**
     * 依次恢复所有组件，保留第一个异常及后续被抑制异常。
     *
     * @param components 按顺序恢复的共享组件
     */
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
            throw new jakarta.ws.rs.NotAuthorizedException("invalid credentials");
        }
        var principal = new AuthPrincipal().setLoginId(username).setUserId(username);
        return new SaTokenSubjectProvider().getSubject().login(AuthRequest.of(username).setPrincipal(principal));
    }

    /**
     * 关闭本实例注册的原生资源并恢复仍由本实例持有的配置。
     */
    @PreDestroy
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
                    if (SaManager.config == config) {
                        SaManager.setConfig(previousConfig);
                    }
                },
                () -> {
                    if (SubjectKit.dataProvider == data) {
                        SubjectKit.setDataProvider(previousData);
                    }
                });
    }
}
