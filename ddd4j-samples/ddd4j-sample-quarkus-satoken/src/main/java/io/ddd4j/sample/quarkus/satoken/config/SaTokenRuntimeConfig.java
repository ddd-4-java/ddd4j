package io.ddd4j.sample.quarkus.satoken.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.util.ReflectUtil;
import io.ddd4j.auth.satoken.subject.SaTokenSubjectDataBridge;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.Objects;

/**
 * 装配原生会话和权限，并在退出时恢复本样例仍占有的组件。
 */
@ApplicationScoped
public class SaTokenRuntimeConfig {

    /**
     * 装配示例之前存在的原生权限接口。
     */
    private StpInterface previousPermissions;
    /**
     * 由当前示例登记的权限接口实现。
     */
    private StpInterface ownedPermissions;
    /**
     * 组件装配前保存的配置，供退出时恢复。
     */
    private SaTokenConfig previousConfig;
    /**
     * 由当前示例安装并在退出时恢复的 Sa-Token 配置。
     */
    private SaTokenConfig ownedConfig;
    /**
     * 由当前示例登记并关闭的会话数据访问组件。
     */
    private SaTokenDao ownedDao;

    /**
     * 依次执行原生配置恢复操作，并保留最初的恢复异常。
     *
     * @param restorations 要依次执行的认证组件恢复动作
     */
    private static void restoreComponents(Runnable... restorations) {
        Throwable failure = null;
        for (Runnable restoration : restorations) {
            try {
                restoration.run();
            } catch (RuntimeException | Error cleanupFailure) {
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

    // 1.45.0 的公开 getter 会初始化组件，按原值保存或比较可空槽位。

    /**
     * 读取已安装的原生权限组件，保留尚未安装的状态。
     *
     * @return 当前 Sa-Token 权限组件
     */
    private static StpInterface currentPermissions() {
        return (StpInterface) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(
                ReflectUtil.getField(SaManager.class, "stpInterface")));
    }

    /**
     * 读取已安装的原生会话存储，避免为查询创建默认存储。
     *
     * @return 当前 Sa-Token 会话存储
     */
    private static SaTokenDao currentDao() {
        return (SaTokenDao) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(
                ReflectUtil.getField(SaManager.class, "saTokenDao")));
    }

    /**
     * 在 Quarkus 启动期间安装示例的 Sa-Token 组件。
     *
     * @param event 启动、停止或请求处理期间的框架事件
     */
    void start(@Observes @Priority(1) StartupEvent event) {
        previousPermissions = currentPermissions();
        previousConfig = SaManager.config;
        ownedPermissions = new SaTokenSubjectDataBridge();
        ownedConfig = new SaTokenConfig().setIsPrint(false).setDataRefreshPeriod(-1);
        try {
            SaManager.setStpInterface(ownedPermissions);
            SaManager.setConfig(ownedConfig);
            // SDK 的 setter 会销毁现有 DAO，调用方已经注册的存储由样例借用。
            if (Objects.isNull(currentDao())) {
                ownedDao = new SaTokenDaoDefaultImpl();
                SaManager.setSaTokenDao(ownedDao);
            }
        } catch (RuntimeException | Error failure) {
            try {
                destroy();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    /**
     * 在 Quarkus 应用关闭时恢复启动之前的原生组件。
     *
     * @param event 启动、停止或请求处理期间的框架事件
     */
    void stop(@Observes ShutdownEvent event) {
        destroy();
    }

    /**
     * 撤销示例持有的 Sa-Token 组件注册并恢复原状态。
     */
    @PreDestroy
    void destroy() {
        StpInterface permissions = ownedPermissions;
        SaTokenConfig config = ownedConfig;
        SaTokenDao dao = ownedDao;
        ownedPermissions = null;
        ownedConfig = null;
        ownedDao = null;

        // SDK 通知监听器可以抛错，退出时仍须处理剩余资源并保留最早的错误。
        restoreComponents(
                () -> {
                    if (Objects.nonNull(permissions) && currentPermissions() == permissions) {
                        SaManager.setStpInterface(previousPermissions);
                    }
                },
                () -> {
                    if (Objects.nonNull(config) && SaManager.config == config) {
                        SaManager.setConfig(previousConfig);
                    }
                },
                () -> {
                    if (Objects.nonNull(dao) && currentDao() == dao) {
                        SaManager.setSaTokenDao(null);
                    }
                });
    }
}
