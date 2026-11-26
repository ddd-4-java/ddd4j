package io.ddd4j.auth.satoken.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.json.SaJsonTemplate;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpInterfaceDefaultImpl;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ReflectUtil;
import io.ddd4j.auth.satoken.subject.SaTokenSubjectDataBridge;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.util.SubjectKit;
import lombok.Builder;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 注册非 Spring 运行时的 Sa-Token 组件，关闭时恢复仍由本次装配持有的设置。
 */
public final class SaTokenRuntimeRegistration implements AutoCloseable {

    private static final List<SaTokenRuntimeRegistration> ACTIVE = new ArrayList<>();
    private static boolean changing;

    private final SaTokenContext context;
    private final SaTokenConfig nativeConfig;
    private final SaTokenSecurityProperties security;
    private final StpLogic customLogic;
    private final StpInterface customPermissions;
    private final SubjectDataProvider subjectData;
    private final SaTokenDao dao;
    private final SaJsonTemplate jsonTemplate;
    private final List<Change> changes = new ArrayList<>();
    private final Map<Change, Supplier<?>> borrowedComponents = new HashMap<>();
    private boolean cleanupPending;

    @Builder
    private SaTokenRuntimeRegistration(SaTokenContext context, SaTokenConfig nativeConfig,
                                       SaTokenSecurityProperties security, StpLogic customLogic,
                                       StpInterface customPermissions, SubjectDataProvider subjectData,
                                       SaTokenDao dao, SaJsonTemplate jsonTemplate) {
        this.context = context;
        this.nativeConfig = nativeConfig;
        this.security = security;
        this.customLogic = customLogic;
        this.customPermissions = customPermissions;
        this.subjectData = subjectData;
        this.dao = dao;
        this.jsonTemplate = jsonTemplate;
    }

    private static void restoreDefaultLogic(StpLogic previous) {
        String realm = previous.getLoginType();
        StpLogic currentRealm = SaManager.stpLogicMap.get(realm);
        try {
            StpUtil.setStpLogic(previous);
        } finally {
            // 原生 setter 同时写入 realm，保留独立的 realm 所有权。
            if (SaManager.stpLogicMap.get(realm) == previous) {
                restoreRealm(realm, currentRealm);
            }
        }
    }

    private static void restoreRealm(String realm, StpLogic previous) {
        if (Objects.isNull(previous)) {
            SaManager.removeStpLogic(realm);
        } else {
            SaManager.putStpLogic(previous);
        }
    }

    private static StpInterface currentPermissions() {
        return component("stpInterface", StpInterface.class);
    }

    private static SaTokenDao currentDao() {
        return component("saTokenDao", SaTokenDao.class);
    }

    private static <T> T component(String field, Class<T> type) {
        return type.cast(ReflectUtil.getStaticFieldValue(Objects.requireNonNull(
                ReflectUtil.getField(SaManager.class, field), "Missing Sa-Token component field: " + field)));
    }

    private static void checkNotChanging() {
        if (changing) {
            throw new IllegalStateException("Sa-Token registration is changing");
        }
    }

    /**
     * 在 HTTP 服务接收请求前完成注册。应用提供的逻辑和权限源优先。
     * 显式替换 DAO 时使用 SDK 自带的销毁和初始化流程。
     */
    public void start() {
        synchronized (ACTIVE) {
            checkNotChanging();
            if (ACTIVE.contains(this)) {
                if (cleanupPending) {
                    throw new IllegalStateException("Close the failed Sa-Token registration before restarting it");
                }
                return;
            }
            changing = true;
            try {
                installComponents();
                ACTIVE.add(this);
            } catch (RuntimeException | Error failure) {
                try {
                    restoreComponents();
                } catch (RuntimeException | Error cleanupFailure) {
                    cleanupPending = true;
                    ACTIVE.add(this);
                    if (failure != cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                throw failure;
            } finally {
                changing = false;
            }
        }
    }

    /**
     * 恢复当前仍持有的组件。后续装配如果会恢复本次组件，须先关闭该装配。
     */
    @Override
    public void close() {
        synchronized (ACTIVE) {
            checkNotChanging();
            int index = ACTIVE.indexOf(this);
            if (index < 0) {
                return;
            }
            for (int i = index + 1; i < ACTIVE.size(); i++) {
                if (ACTIVE.get(i).dependsOn(this)) {
                    throw new IllegalStateException("Close the dependent Sa-Token registration first");
                }
            }
            changing = true;
            try {
                restoreComponents();
            } finally {
                cleanupPending = !changes.isEmpty();
                if (!cleanupPending) {
                    ACTIVE.remove(index);
                }
                changing = false;
            }
        }
    }

    private void installComponents() {
        boolean configureSecurity = Objects.isNull(customLogic) && Objects.nonNull(security);
        if (configureSecurity) {
            security.validate();
        }
        install(Slot.CONFIG, () -> SaManager.config, SaManager::setConfig, nativeConfig);
        if (configureSecurity) {
            rememberSecuritySettings();
            installLogic(security.getLoginType(), () -> SaTokenSecurityConfigurer.createConfiguredLogic(security));
        } else if (Objects.nonNull(customLogic)) {
            installLogic(customLogic.getLoginType(), () -> customLogic);
        }
        install(Slot.SUBJECT_DATA, () -> SubjectKit.dataProvider, SubjectKit::setDataProvider, subjectData);
        StpInterface permissions = currentPermissions();
        StpInterface nextPermissions = customPermissions;
        if (Objects.isNull(nextPermissions)
                && (Objects.isNull(permissions) || permissions.getClass() == StpInterfaceDefaultImpl.class)) {
            nextPermissions = new SaTokenSubjectDataBridge();
        }
        install(Slot.PERMISSIONS, SaTokenRuntimeRegistration::currentPermissions, SaManager::setStpInterface, nextPermissions);
        install(Slot.CONTEXT, () -> component("saTokenContext", SaTokenContext.class), SaManager::setSaTokenContext, context);
        install(Slot.JSON, () -> component("saJsonTemplate", SaJsonTemplate.class), SaManager::setSaJsonTemplate, jsonTemplate);
        // getter 会在空槽创建并启动默认 DAO，快照与所有权判断都读取原槽位。
        install(Slot.DAO, SaTokenRuntimeRegistration::currentDao, SaManager::setSaTokenDao, dao);
    }

    private <T> void install(Slot slot, Supplier<T> read, Consumer<T> write, T value) {
        if (Objects.isNull(value)) {
            return;
        }
        T previous = read.get();
        if (value != previous) {
            // SDK setter 可能发布组件后才抛错，先记录补偿动作。
            remember(slot, null, read, write, previous, value);
            write.accept(value);
        } else {
            borrow(slot, null, read, value);
        }
    }

    private <T> void borrow(Slot slot, String realm, Supplier<T> read, T value) {
        // 同一实例无需再次注册，只依赖持有该槽位的装配。
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            for (Change change : ACTIVE.get(i).changes) {
                if (change.slot == slot && Objects.equals(change.realm, realm)
                        && change.installed == value && change.previous != value) {
                    borrowedComponents.put(change, read);
                    return;
                }
            }
        }
    }

    private <T> void remember(Slot slot, String realm, Supplier<T> read, Consumer<T> write, T previous, T value) {
        changes.add(new Change(slot, realm, previous, value, new Runnable() {
            private boolean retryPublishedDao;

            @Override
            public void run() {
                T current = read.get();
                if (current != value && !(retryPublishedDao && current == previous)) {
                    return;
                }
                try {
                    write.accept(previous);
                } catch (RuntimeException | Error failure) {
                    if (slot == Slot.DAO) {
                        // DAO 发布后，初始化或组件通知仍可能失败。仍在槽位时由 SDK 重试。
                        retryPublishedDao = read.get() == previous;
                    }
                    throw failure;
                }
            }
        }));
    }

    private void rememberSecuritySettings() {
        SaTokenConfig previousConfig = SaManager.config;
        SaTokenConfig configured = Objects.requireNonNull(SaManager.getConfig(), "Sa-Token configuration must not be null");
        boolean managerConfig = configured == SaManager.config;
        if (Objects.isNull(previousConfig) && managerConfig) {
            remember(Slot.CONFIG, null, () -> SaManager.config, SaManager::setConfig, null, configured);
        }
        StpLogic previousLogic = StpUtil.stpLogic;
        String previousTokenName = configured.getTokenName();
        long previousTimeout = configured.getTimeout();
        long previousActiveTimeout = configured.getActiveTimeout();
        Boolean previousTokenSessionCheckLogin = configured.getTokenSessionCheckLogin();
        changes.add(new Change(Slot.CONFIG, null, configured, configured, () -> {
            SaTokenConfig currentConfig = managerConfig ? SaManager.config : SaManager.getConfig();
            if (currentConfig == configured && StpUtil.stpLogic == previousLogic) {
                configured.setTokenName(previousTokenName);
                configured.setTimeout(previousTimeout);
                configured.setActiveTimeout(previousActiveTimeout);
                configured.setTokenSessionCheckLogin(previousTokenSessionCheckLogin);
            }
        }));
    }

    private void installLogic(String realm, Supplier<StpLogic> create) {
        Supplier<StpLogic> readDefault = () -> StpUtil.stpLogic;
        Supplier<StpLogic> readRealm = () -> SaManager.stpLogicMap.get(realm);
        StpLogic previousDefault = readDefault.get();
        StpLogic previousRealm = readRealm.get();
        StpLogic expected = create.get();
        try {
            StpUtil.setStpLogic(expected);
        } finally {
            trackLogic(Slot.REALM, realm, readRealm, value -> restoreRealm(realm, value), previousRealm, expected);
            trackLogic(Slot.DEFAULT_LOGIC, null, readDefault,
                    SaTokenRuntimeRegistration::restoreDefaultLogic, previousDefault, expected);
        }
    }

    private void trackLogic(Slot slot, String realm, Supplier<StpLogic> read, Consumer<StpLogic> write,
                            StpLogic previous, StpLogic expected) {
        // 注册通知可替换默认逻辑或登录域，只跟踪仍指向本次所选实例的槽位。
        if (read.get() != expected) {
            return;
        }
        if (previous == expected) {
            borrow(slot, realm, read, expected);
        } else {
            remember(slot, realm, read, write, previous, expected);
        }
    }

    private boolean dependsOn(SaTokenRuntimeRegistration previous) {
        for (Map.Entry<Change, Supplier<?>> borrow : borrowedComponents.entrySet()) {
            Change owner = borrow.getKey();
            if (previous.changes.contains(owner) && borrow.getValue().get() == owner.installed) {
                return true;
            }
        }
        for (Change next : changes) {
            for (Change earlier : previous.changes) {
                if (next.slot == earlier.slot && Objects.equals(next.realm, earlier.realm)
                        && next.previous == earlier.installed) {
                    return true;
                }
            }
        }
        return false;
    }

    private void restoreComponents() {
        borrowedComponents.clear();
        Throwable failure = null;
        // 先恢复原配置和上下文，原 DAO 随后由 SDK 在该环境下重新初始化。
        for (Slot slot : Slot.values()) {
            for (int i = changes.size() - 1; i >= 0; i--) {
                Change change = changes.get(i);
                if (change.slot != slot) {
                    continue;
                }
                try {
                    change.restore.run();
                    changes.remove(i);
                } catch (RuntimeException | Error cleanupFailure) {
                    if (Objects.isNull(failure)) {
                        failure = cleanupFailure;
                    } else if (failure != cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
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

    private enum Slot {
        DEFAULT_LOGIC, REALM, CONFIG, CONTEXT, PERMISSIONS, SUBJECT_DATA, DAO, JSON
    }

    private static final class Change {

        private final Slot slot;
        private final String realm;
        private final Object previous;
        private final Object installed;
        private final Runnable restore;

        private Change(Slot slot, String realm, Object previous, Object installed, Runnable restore) {
            this.slot = slot;
            this.realm = realm;
            this.previous = previous;
            this.installed = installed;
            this.restore = restore;
        }
    }
}
