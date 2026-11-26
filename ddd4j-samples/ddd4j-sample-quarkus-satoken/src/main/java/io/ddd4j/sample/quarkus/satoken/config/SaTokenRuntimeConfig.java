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
import javax.annotation.PreDestroy;
import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import java.util.Objects;

/**
 * 装配原生会话和权限，并在退出时恢复本样例仍占有的组件。
 */
@ApplicationScoped
public class SaTokenRuntimeConfig {

    private StpInterface previousPermissions;
    private StpInterface ownedPermissions;
    private SaTokenConfig previousConfig;
    private SaTokenConfig ownedConfig;
    private SaTokenDao ownedDao;

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
    private static StpInterface currentPermissions() {
        return (StpInterface) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(
                ReflectUtil.getField(SaManager.class, "stpInterface")));
    }

    private static SaTokenDao currentDao() {
        return (SaTokenDao) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(
                ReflectUtil.getField(SaManager.class, "saTokenDao")));
    }

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

    void stop(@Observes ShutdownEvent event) {
        destroy();
    }

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
