package io.ddd4j.sample.micronaut.satoken;

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
import io.micronaut.context.annotation.Context;
import jakarta.annotation.PreDestroy;

import java.util.List;
import java.util.Objects;

/**
 * 单应用拥有的原生 Sa-Token 会话及权限提供器；关闭恢复前任状态。
 */
@Context
public final class NativeAuth {
    /** \u5b89\u88c5\u793a\u4f8b\u7ec4\u4ef6\u524d\u4fdd\u5b58\u7684 Sa-Token \u914d\u7f6e\u3002 */
    private final SaTokenConfig previousConfig = SaManager.config;
    /** \u5b89\u88c5\u793a\u4f8b\u7ec4\u4ef6\u524d\u4fdd\u5b58\u7684 Sa-Token \u6743\u9650\u63d0\u4f9b\u5668\u3002 */
    private final StpInterface previousPermissions = currentPermissions();
    /** \u672c\u793a\u4f8b\u5b89\u88c5\u7684 Sa-Token \u4f1a\u8bdd\u914d\u7f6e\u3002 */
    private final SaTokenConfig config;
    /** \u5c06 DDD4J \u4e3b\u4f53\u89d2\u8272\u548c\u6743\u9650\u6570\u636e\u63a5\u5165 Sa-Token \u7684\u6865\u63a5\u7ec4\u4ef6\u3002 */
    private final StpInterface permissions;
    /** \u5b89\u88c5\u793a\u4f8b\u7ec4\u4ef6\u524d\u4fdd\u5b58\u7684 DDD4J \u4e3b\u4f53\u6570\u636e\u63d0\u4f9b\u5668\u3002 */
    private final SubjectDataProvider previousData = SubjectKit.dataProvider;
    /** \u4e3a\u793a\u4f8b\u8d26\u53f7\u63d0\u4f9b\u6743\u9650\u548c\u89d2\u8272\u6570\u636e\u7684 DDD4J \u6570\u636e\u63d0\u4f9b\u5668\u3002 */
    private final SubjectDataProvider data;
    /** \u4ec5\u5728\u539f DAO \u4e3a\u7a7a\u65f6\u6ce8\u518c\u7684\u793a\u4f8b\u4f1a\u8bdd\u5b58\u50a8\u3002 */
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
     * \u8bfb\u53d6 Sa-Token DAO \u7684\u539f\u59cb\u69fd\u4f4d\uff0c\u907f\u514d\u521b\u5efa\u9ed8\u8ba4\u4f1a\u8bdd\u5b58\u50a8\u3002
     * @return \u5df2\u6ce8\u518c\u7684\u4f1a\u8bdd\u5b58\u50a8\uff0c\u53ef\u4ee5\u4e3a\u7a7a
     */
    private static SaTokenDao currentDao() {
        return (SaTokenDao) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(
                ReflectUtil.getField(SaManager.class, "saTokenDao")));
    }

    /**
     * \u8bfb\u53d6\u6743\u9650\u63d0\u4f9b\u5668\u7684\u539f\u59cb\u69fd\u4f4d\uff0c\u907f\u514d getter \u5b89\u88c5\u9ed8\u8ba4\u7ec4\u4ef6\u3002
     * @return \u5f53\u524d\u7684\u6743\u9650\u63d0\u4f9b\u5668\uff0c\u53ef\u4ee5\u4e3a\u7a7a
     */
    private static StpInterface currentPermissions() {
        return (StpInterface) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(
                ReflectUtil.getField(SaManager.class, "stpInterface")));
    }

    /**
     * \u4f9d\u6b21\u6062\u590d\u6240\u6709\u7ec4\u4ef6\uff0c\u4fdd\u7559\u7b2c\u4e00\u4e2a\u5f02\u5e38\u53ca\u540e\u7eed\u88ab\u6291\u5236\u5f02\u5e38\u3002
     * @param components \u6309\u987a\u5e8f\u6062\u590d\u7684\u5171\u4eab\u7ec4\u4ef6
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
            throw new io.micronaut.http.exceptions.HttpStatusException(io.micronaut.http.HttpStatus.UNAUTHORIZED, "invalid credentials");
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
