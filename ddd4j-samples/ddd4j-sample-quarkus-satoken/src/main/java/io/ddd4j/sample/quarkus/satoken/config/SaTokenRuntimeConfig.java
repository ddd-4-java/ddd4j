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

    /** \u88c5\u914d\u793a\u4f8b\u4e4b\u524d\u5b58\u5728\u7684\u539f\u751f\u6743\u9650\u63a5\u53e3\u3002 */
    private StpInterface previousPermissions;
    /** \u7531\u5f53\u524d\u793a\u4f8b\u767b\u8bb0\u7684\u6743\u9650\u63a5\u53e3\u5b9e\u73b0\u3002 */
    private StpInterface ownedPermissions;
    /** \u7ec4\u4ef6\u88c5\u914d\u524d\u4fdd\u5b58\u7684\u914d\u7f6e\uff0c\u4f9b\u9000\u51fa\u65f6\u6062\u590d\u3002 */
    private SaTokenConfig previousConfig;
    /** \u7531\u5f53\u524d\u793a\u4f8b\u5b89\u88c5\u5e76\u5728\u9000\u51fa\u65f6\u6062\u590d\u7684 Sa-Token \u914d\u7f6e\u3002 */
    private SaTokenConfig ownedConfig;
    /** \u7531\u5f53\u524d\u793a\u4f8b\u767b\u8bb0\u5e76\u5173\u95ed\u7684\u4f1a\u8bdd\u6570\u636e\u8bbf\u95ee\u7ec4\u4ef6\u3002 */
    private SaTokenDao ownedDao;

    /**
     * \u4f9d\u6b21\u6267\u884c\u539f\u751f\u914d\u7f6e\u6062\u590d\u64cd\u4f5c\uff0c\u5e76\u4fdd\u7559\u6700\u521d\u7684\u6062\u590d\u5f02\u5e38\u3002
     * @param restorations \u8981\u4f9d\u6b21\u6267\u884c\u7684\u8ba4\u8bc1\u7ec4\u4ef6\u6062\u590d\u52a8\u4f5c
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
     * \u8bfb\u53d6\u5df2\u5b89\u88c5\u7684\u539f\u751f\u6743\u9650\u7ec4\u4ef6\uff0c\u4fdd\u7559\u5c1a\u672a\u5b89\u88c5\u7684\u72b6\u6001\u3002
     * @return \u5f53\u524d Sa-Token \u6743\u9650\u7ec4\u4ef6
     */
    private static StpInterface currentPermissions() {
        return (StpInterface) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(
                ReflectUtil.getField(SaManager.class, "stpInterface")));
    }

    /**
     * \u8bfb\u53d6\u5df2\u5b89\u88c5\u7684\u539f\u751f\u4f1a\u8bdd\u5b58\u50a8\uff0c\u907f\u514d\u4e3a\u67e5\u8be2\u521b\u5efa\u9ed8\u8ba4\u5b58\u50a8\u3002
     * @return \u5f53\u524d Sa-Token \u4f1a\u8bdd\u5b58\u50a8
     */
    private static SaTokenDao currentDao() {
        return (SaTokenDao) ReflectUtil.getStaticFieldValue(Objects.requireNonNull(
                ReflectUtil.getField(SaManager.class, "saTokenDao")));
    }

    /**
     * \u5728 Quarkus \u542f\u52a8\u671f\u95f4\u5b89\u88c5\u793a\u4f8b\u7684 Sa-Token \u7ec4\u4ef6\u3002
     * @param event \u542f\u52a8\u3001\u505c\u6b62\u6216\u8bf7\u6c42\u5904\u7406\u671f\u95f4\u7684\u6846\u67b6\u4e8b\u4ef6
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
     * \u5728 Quarkus \u5e94\u7528\u5173\u95ed\u65f6\u6062\u590d\u542f\u52a8\u4e4b\u524d\u7684\u539f\u751f\u7ec4\u4ef6\u3002
     * @param event \u542f\u52a8\u3001\u505c\u6b62\u6216\u8bf7\u6c42\u5904\u7406\u671f\u95f4\u7684\u6846\u67b6\u4e8b\u4ef6
     */
    void stop(@Observes ShutdownEvent event) {
        destroy();
    }

    /** \u64a4\u9500\u793a\u4f8b\u6301\u6709\u7684 Sa-Token \u7ec4\u4ef6\u6ce8\u518c\u5e76\u6062\u590d\u539f\u72b6\u6001\u3002 */
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
