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

    /** \u6309\u5b89\u88c5\u987a\u5e8f\u6301\u6709\u4ecd\u6709\u6548\u7684 Sa-Token \u8fd0\u884c\u65f6\u6ce8\u518c\u3002 */
    private static final List<SaTokenRuntimeRegistration> ACTIVE = new ArrayList<>();
    /** \u6807\u8bb0\u5171\u4eab\u8ba4\u8bc1\u7ec4\u4ef6\u6b63\u5728\u5207\u6362\u3002 */
    private static boolean changing;

    /** \u5f53\u524d\u6ce8\u518c\u8981\u5b89\u88c5\u7684 Sa-Token \u8bf7\u6c42\u4e0a\u4e0b\u6587\u3002 */
    private final SaTokenContext context;
    /** \u5f53\u524d\u6ce8\u518c\u8981\u4f7f\u7528\u7684\u539f\u751f Sa-Token \u914d\u7f6e\u3002 */
    private final SaTokenConfig nativeConfig;
    /** \u751f\u6210\u9ed8\u8ba4\u8ba4\u8bc1\u903b\u8f91\u65f6\u4f7f\u7528\u7684\u5b89\u5168\u5c5e\u6027\u3002 */
    private final SaTokenSecurityProperties security;
    /** \u8c03\u7528\u65b9\u663e\u5f0f\u6307\u5b9a\u7684 Sa-Token \u767b\u5f55\u903b\u8f91\u3002 */
    private final StpLogic customLogic;
    /** \u8c03\u7528\u65b9\u63d0\u4f9b\u7684\u6743\u9650\u4e0e\u89d2\u8272\u6570\u636e\u6865\u63a5\u3002 */
    private final StpInterface customPermissions;
    /** \u4e3a\u8ba4\u8bc1\u4e3b\u4f53\u63d0\u4f9b\u89d2\u8272\u3001\u6743\u9650\u7b49\u4e1a\u52a1\u6570\u636e\u7684\u7ec4\u4ef6\u3002 */
    private final SubjectDataProvider subjectData;
    /** \u5f53\u524d\u88c5\u914d\u8981\u63d0\u4f9b\u7684 Sa-Token \u4f1a\u8bdd\u5b58\u50a8\u3002 */
    private final SaTokenDao dao;
    /** Sa-Token \u5185\u90e8\u4f7f\u7528\u7684 JSON \u7f16\u89e3\u7801\u6a21\u677f\u3002 */
    private final SaJsonTemplate jsonTemplate;
    /** \u7531\u5f53\u524d\u6ce8\u518c\u62e5\u6709\u4e14\u5173\u95ed\u65f6\u5e94\u64a4\u9500\u7684\u7ec4\u4ef6\u53d8\u66f4\u3002 */
    private final List<Change> changes = new ArrayList<>();
    /** \u590d\u7528\u5916\u90e8\u5df2\u5b89\u88c5\u7ec4\u4ef6\u65f6\u6301\u6709\u7684\u4f9d\u8d56\u5173\u7cfb\u8bb0\u5f55\u3002 */
    private final Map<Change, Supplier<?>> borrowedComponents = new HashMap<>();
    /** \u9996\u6b21\u5173\u95ed\u672a\u5f7b\u5e95\u5b8c\u6210\u4e14\u4ecd\u987b\u91cd\u8bd5\u6e05\u7406\u7684\u6807\u8bb0\u3002 */
    private boolean cleanupPending;

    /**
     * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>SaTokenRuntimeRegistration</code> \u5b9e\u4f8b\u3002
     * @param context <code>context</code> \u5b57\u6bb5
     * @param nativeConfig <code>nativeConfig</code> \u5b57\u6bb5
     * @param security <code>security</code> \u5b57\u6bb5
     * @param customLogic <code>customLogic</code> \u5b57\u6bb5
     * @param customPermissions <code>customPermissions</code> \u5b57\u6bb5
     * @param subjectData \u4e3a\u8ba4\u8bc1\u4e3b\u4f53\u63d0\u4f9b\u89d2\u8272\u3001\u6743\u9650\u7b49\u4e1a\u52a1\u6570\u636e\u7684\u7ec4\u4ef6
     * @param dao <code>dao</code> \u5b57\u6bb5
     * @param jsonTemplate <code>jsonTemplate</code> \u5b57\u6bb5
     */
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

    /**
     * \u6062\u590d\u539f\u9ed8\u8ba4\u767b\u5f55\u903b\u8f91\uff0c\u540c\u65f6\u4fdd\u7559\u72ec\u7acb\u8ba4\u8bc1\u57df\u69fd\u4f4d\u7684\u5f53\u524d\u6240\u6709\u6743\u3002
     * @param previous \u9700\u8981\u6062\u590d\u6216\u5224\u65ad\u662f\u5426\u88ab\u5f53\u524d\u6ce8\u518c\u4f9d\u8d56\u7684\u65e7\u503c
     */
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

    /**
     * \u6062\u590d\u6307\u5b9a\u8ba4\u8bc1\u57df\u7684\u65e7\u767b\u5f55\u903b\u8f91\uff0c\u539f\u503c\u4e3a\u7a7a\u65f6\u5220\u9664\u767b\u8bb0\u3002
     * @param realm \u5f53\u524d\u8ba4\u8bc1\u57df\u540d\u79f0
     * @param previous \u9700\u8981\u6062\u590d\u6216\u5224\u65ad\u662f\u5426\u88ab\u5f53\u524d\u6ce8\u518c\u4f9d\u8d56\u7684\u65e7\u503c
     */
    private static void restoreRealm(String realm, StpLogic previous) {
        if (Objects.isNull(previous)) {
            SaManager.removeStpLogic(realm);
        } else {
            SaManager.putStpLogic(previous);
        }
    }

    /**
     * \u5728\u4e0d\u89e6\u53d1\u539f\u751f\u7ec4\u4ef6\u521d\u59cb\u5316\u7684\u60c5\u51b5\u4e0b\u8bfb\u53d6\u6743\u9650\u69fd\u4f4d\u3002
     * @return \u5df2\u5b89\u88c5\u7684\u6743\u9650\u63d0\u4f9b\u5668\uff0c\u7f3a\u5c11\u65f6\u4e3a\u7a7a
     */
    private static StpInterface currentPermissions() {
        return component("stpInterface", StpInterface.class);
    }

    /**
     * \u5728\u4e0d\u89e6\u53d1\u539f\u751f\u7ec4\u4ef6\u521d\u59cb\u5316\u7684\u60c5\u51b5\u4e0b\u8bfb\u53d6\u5b58\u50a8\u69fd\u4f4d\u3002
     * @return \u5df2\u5b89\u88c5\u7684\u539f\u751f\u4f1a\u8bdd\u5b58\u50a8\uff0c\u7f3a\u5c11\u65f6\u4e3a\u7a7a
     */
    private static SaTokenDao currentDao() {
        return component("saTokenDao", SaTokenDao.class);
    }

    /**
     * \u4ee5\u7ed9\u5b9a\u7c7b\u578b\u8bfb\u53d6 Sa-Token \u7ba1\u7406\u5668\u9759\u6001\u69fd\u4f4d\u4e2d\u7684\u539f\u59cb\u503c\u3002
     * @param <T> \u539f\u751f\u7ec4\u4ef6\u7c7b\u578b
     * @param field \u539f\u751f\u7ba1\u7406\u5668\u4e2d\u8981\u8bfb\u53d6\u7684\u9759\u6001\u5b57\u6bb5\u540d\u79f0
     * @param type \u8be5\u5b57\u6bb5\u6216\u56de\u8c03\u7ed3\u679c\u5bf9\u5e94\u7684 Java \u7c7b\u578b
     * @return \u539f\u751f\u5b57\u6bb5\u4e2d\u73b0\u5b58\u7684\u7ec4\u4ef6\u5b9e\u4f8b
     */
    private static <T> T component(String field, Class<T> type) {
        return type.cast(ReflectUtil.getStaticFieldValue(Objects.requireNonNull(
                ReflectUtil.getField(SaManager.class, field), "Missing Sa-Token component field: " + field)));
    }

    /**
     * \u7981\u6b62\u5728\u5171\u4eab\u6ce8\u518c\u5207\u6362\u5c1a\u672a\u5b8c\u6210\u65f6\u518d\u6b21\u4fee\u6539\u7ec4\u4ef6\u3002
     * @throws IllegalStateException \u5171\u4eab\u6ce8\u518c\u6b63\u5728\u5207\u6362
     */
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

    /** \u6309\u914d\u7f6e\u88c5\u914d\u8ba4\u8bc1\u903b\u8f91\u3001\u539f\u751f\u7ec4\u4ef6\u53ca\u5176\u56de\u6eda\u8bb0\u5f55\u3002 */
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

    /**
     * \u5728\u7ed9\u5b9a\u7ec4\u4ef6\u540e\u8bb0\u5f55\u53ef\u6062\u590d\u65e7\u503c\u5e76\u66f4\u65b0\u69fd\u4f4d\uff0c\u5df2\u6709\u540c\u4e00\u5b9e\u4f8b\u65f6\u53ea\u767b\u8bb0\u4f9d\u8d56\u3002
     * @param <T> \u5f53\u524d\u69fd\u4f4d\u7684\u7ec4\u4ef6\u7c7b\u578b
     * @param slot \u5f53\u524d\u88ab\u4fee\u6539\u6216\u590d\u7528\u7684\u5171\u4eab\u7ec4\u4ef6\u69fd\u4f4d
     * @param read \u8bfb\u53d6\u8be5\u69fd\u4f4d\u5f53\u524d\u503c\u7684\u51fd\u6570
     * @param write \u5199\u5165\u6216\u6062\u590d\u69fd\u4f4d\u503c\u7684\u56de\u8c03
     * @param value \u5f53\u524d\u64cd\u4f5c\u51c6\u5907\u5199\u5165\u3001\u590d\u7528\u6216\u89c4\u8303\u5316\u7684\u503c
     */
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

    /**
     * \u590d\u7528\u5176\u4ed6\u5df2\u5b89\u88c5\u7684\u540c\u4e00\u7ec4\u4ef6\uff0c\u8ffd\u8e2a\u8be5\u69fd\u4f4d\u539f\u6709\u7684\u6240\u6709\u8005\u3002
     * @param <T> \u88ab\u590d\u7528\u7684\u7ec4\u4ef6\u7c7b\u578b
     * @param slot \u5f53\u524d\u88ab\u4fee\u6539\u6216\u590d\u7528\u7684\u5171\u4eab\u7ec4\u4ef6\u69fd\u4f4d
     * @param realm \u5f53\u524d\u8ba4\u8bc1\u57df\u540d\u79f0
     * @param read \u8bfb\u53d6\u8be5\u69fd\u4f4d\u5f53\u524d\u503c\u7684\u51fd\u6570
     * @param value \u5f53\u524d\u64cd\u4f5c\u51c6\u5907\u5199\u5165\u3001\u590d\u7528\u6216\u89c4\u8303\u5316\u7684\u503c
     */
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

    /**
     * \u767b\u8bb0\u5f53\u524d\u7ec4\u4ef6\u53d8\u66f4\u4ee5\u53ca\u9000\u51fa\u65f6\u4ecd\u7b26\u5408\u6240\u6709\u6743\u6761\u4ef6\u7684\u6062\u590d\u52a8\u4f5c\u3002
     * @param <T> \u5f53\u524d\u69fd\u4f4d\u7684\u7ec4\u4ef6\u7c7b\u578b
     * @param slot \u5f53\u524d\u88ab\u4fee\u6539\u6216\u590d\u7528\u7684\u5171\u4eab\u7ec4\u4ef6\u69fd\u4f4d
     * @param realm \u5f53\u524d\u8ba4\u8bc1\u57df\u540d\u79f0
     * @param read \u8bfb\u53d6\u8be5\u69fd\u4f4d\u5f53\u524d\u503c\u7684\u51fd\u6570
     * @param write \u5199\u5165\u6216\u6062\u590d\u69fd\u4f4d\u503c\u7684\u56de\u8c03
     * @param previous \u9700\u8981\u6062\u590d\u6216\u5224\u65ad\u662f\u5426\u88ab\u5f53\u524d\u6ce8\u518c\u4f9d\u8d56\u7684\u65e7\u503c
     * @param value \u5f53\u524d\u64cd\u4f5c\u51c6\u5907\u5199\u5165\u3001\u590d\u7528\u6216\u89c4\u8303\u5316\u7684\u503c
     */
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

    /** \u4fdd\u5b58\u539f\u751f\u914d\u7f6e\u548c\u5b89\u5168\u5c5e\u6027\u4ee5\u4fbf\u9000\u51fa\u540e\u6062\u590d\u3002 */
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

    /**
     * \u5b89\u88c5\u6307\u5b9a\u8ba4\u8bc1\u57df\u767b\u5f55\u903b\u8f91\uff0c\u5e76\u5206\u522b\u8ffd\u8e2a\u9ed8\u8ba4\u548c\u8ba4\u8bc1\u57df\u69fd\u4f4d\u7684\u53d8\u5316\u3002
     * @param realm \u5f53\u524d\u8ba4\u8bc1\u57df\u540d\u79f0
     * @param create \u6784\u9020\u5f53\u524d\u8ba4\u8bc1\u57df\u767b\u5f55\u903b\u8f91\u7684\u51fd\u6570
     */
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

    /**
     * \u5728\u5f53\u524d\u69fd\u4f4d\u4ecd\u6307\u5411\u9009\u5b9a\u903b\u8f91\u65f6\u767b\u8bb0\u53d8\u66f4\u6216\u590d\u7528\u4f9d\u8d56\u3002
     * @param slot \u5f53\u524d\u88ab\u4fee\u6539\u6216\u590d\u7528\u7684\u5171\u4eab\u7ec4\u4ef6\u69fd\u4f4d
     * @param realm \u5f53\u524d\u8ba4\u8bc1\u57df\u540d\u79f0
     * @param read \u8bfb\u53d6\u8be5\u69fd\u4f4d\u5f53\u524d\u503c\u7684\u51fd\u6570
     * @param write \u5199\u5165\u6216\u6062\u590d\u69fd\u4f4d\u503c\u7684\u56de\u8c03
     * @param previous \u9700\u8981\u6062\u590d\u6216\u5224\u65ad\u662f\u5426\u88ab\u5f53\u524d\u6ce8\u518c\u4f9d\u8d56\u7684\u65e7\u503c
     * @param expected \u672c\u6b21\u9884\u8ba1\u5199\u5165\u7684\u539f\u751f\u767b\u5f55\u903b\u8f91
     */
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

    /**
     * \u68c0\u67e5\u5f53\u524d\u6ce8\u518c\u662f\u5426\u4ecd\u4f9d\u8d56\u8f83\u65e9\u7684\u8fd0\u884c\u65f6\u6ce8\u518c\u3002
     * @param previous \u8f83\u65e9\u5b89\u88c5\u7684\u8fd0\u884c\u65f6\u6ce8\u518c
     * @return \u4ecd\u6301\u6709\u88ab\u4f9d\u8d56\u88c5\u914d\u62e5\u6709\u7684\u7ec4\u4ef6\u65f6\u8fd4\u56de true
     */
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

    /** \u6309\u5171\u4eab\u69fd\u4f4d\u987a\u5e8f\u6062\u590d\u539f\u7ec4\u4ef6\uff0c\u5e76\u4fdd\u7559\u6062\u590d\u5931\u8d25\u7684\u53d8\u66f4\u4f9b\u91cd\u8bd5\u3002 */
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

    /** \u6807\u8bc6\u8ba4\u8bc1\u8fd0\u884c\u65f6\u4e2d\u53ef\u5b89\u88c5\u548c\u56de\u6eda\u7684\u5171\u4eab\u7ec4\u4ef6\u69fd\u4f4d\u3002 */
    private enum Slot {
        /** Sa-Token \u7684\u9ed8\u8ba4\u767b\u5f55\u903b\u8f91\u3002 */
        DEFAULT_LOGIC,
            /** \u6309\u6807\u8bc6\u533a\u5206\u7684\u767b\u5f55\u57df\u7ec4\u4ef6\u69fd\u4f4d\u3002 */
            REALM,
            /** \u5168\u5c40 Sa-Token \u914d\u7f6e\u69fd\u4f4d\u3002 */
            CONFIG,
            /** Sa-Token \u8bf7\u6c42\u4e0a\u4e0b\u6587\u69fd\u4f4d\u3002 */
            CONTEXT,
            /** \u6743\u9650\u548c\u89d2\u8272\u6570\u636e\u63d0\u4f9b\u69fd\u4f4d\u3002 */
            PERMISSIONS,
            /** DDD4J \u4e3b\u4f53\u6570\u636e\u63d0\u4f9b\u7ec4\u4ef6\u69fd\u4f4d\u3002 */
            SUBJECT_DATA,
            /** \u539f\u751f\u4ee4\u724c\u53ca\u4f1a\u8bdd\u5b58\u50a8\u69fd\u4f4d\u3002 */
            DAO,
            /** \u539f\u751f JSON \u7f16\u89e3\u7801\u7ec4\u4ef6\u69fd\u4f4d\u3002 */
            JSON
    }

    /** \u8bb0\u5f55\u4e00\u6b21 Sa-Token \u5171\u4eab\u6ce8\u518c\u7684\u539f\u7ec4\u4ef6\u3001\u65b0\u7ec4\u4ef6\u53ca\u6062\u590d\u52a8\u4f5c\u3002 */
    private static final class Change {

        /** \u672c\u6b21\u5b89\u88c5\u64cd\u4f5c\u5360\u7528\u7684\u5171\u4eab\u7ec4\u4ef6\u69fd\u4f4d\u3002 */
        private final Slot slot;
        /** \u5f53\u524d\u8ba4\u8bc1\u903b\u8f91\u6216\u8ba4\u8bc1\u4e3b\u4f53\u4f7f\u7528\u7684\u8ba4\u8bc1\u57df\u3002 */
        private final String realm;
        /** \u7ec4\u4ef6\u5b89\u88c5\u524d\u4fdd\u5b58\u7684\u539f\u5bf9\u8c61\u3002 */
        private final Object previous;
        /** \u6ce8\u518c\u540e\u7531\u5f53\u524d\u4f5c\u7528\u57df\u6301\u6709\u7684\u7ec4\u4ef6\u3002 */
        private final Object installed;
        /** \u5728\u9000\u51fa\u6ce8\u518c\u4f5c\u7528\u57df\u65f6\u6062\u590d\u539f\u7ec4\u4ef6\u7684\u52a8\u4f5c\u3002 */
        private final Runnable restore;

        /**
         * \u521b\u5efa\u5173\u8054\u7ec4\u4ef6\u69fd\u4f4d\u3001\u65b0\u65e7\u503c\u548c\u6062\u590d\u52a8\u4f5c\u7684\u53d8\u66f4\u8bb0\u5f55\u3002
         * @param slot \u5f53\u524d\u88ab\u4fee\u6539\u6216\u590d\u7528\u7684\u5171\u4eab\u7ec4\u4ef6\u69fd\u4f4d
         * @param realm \u5f53\u524d\u8ba4\u8bc1\u57df\u540d\u79f0
         * @param previous \u9700\u8981\u6062\u590d\u6216\u5224\u65ad\u662f\u5426\u88ab\u5f53\u524d\u6ce8\u518c\u4f9d\u8d56\u7684\u65e7\u503c
         * @param installed \u5f53\u524d\u6ce8\u518c\u5b89\u88c5\u7684\u7ec4\u4ef6
         * @param restore \u64a4\u9500\u672c\u6b21\u53d8\u66f4\u7684\u52a8\u4f5c
         */
        private Change(Slot slot, String realm, Object previous, Object installed, Runnable restore) {
            this.slot = slot;
            this.realm = realm;
            this.previous = previous;
            this.installed = installed;
            this.restore = restore;
        }
    }
}
