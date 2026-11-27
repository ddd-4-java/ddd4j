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

    /**
     * 按安装顺序持有仍有效的 Sa-Token 运行时注册。
     */
    private static final List<SaTokenRuntimeRegistration> ACTIVE = new ArrayList<>();
    /**
     * 标记共享认证组件正在切换。
     */
    private static boolean changing;

    /**
     * 当前注册要安装的 Sa-Token 请求上下文。
     */
    private final SaTokenContext context;
    /**
     * 当前注册要使用的原生 Sa-Token 配置。
     */
    private final SaTokenConfig nativeConfig;
    /**
     * 生成默认认证逻辑时使用的安全属性。
     */
    private final SaTokenSecurityProperties security;
    /**
     * 调用方显式指定的 Sa-Token 登录逻辑。
     */
    private final StpLogic customLogic;
    /**
     * 调用方提供的权限与角色数据桥接。
     */
    private final StpInterface customPermissions;
    /**
     * 为认证主体提供角色、权限等业务数据的组件。
     */
    private final SubjectDataProvider subjectData;
    /**
     * 当前装配要提供的 Sa-Token 会话存储。
     */
    private final SaTokenDao dao;
    /**
     * Sa-Token 内部使用的 JSON 编解码模板。
     */
    private final SaJsonTemplate jsonTemplate;
    /**
     * 由当前注册拥有且关闭时应撤销的组件变更。
     */
    private final List<Change> changes = new ArrayList<>();
    /**
     * 复用外部已安装组件时持有的依赖关系记录。
     */
    private final Map<Change, Supplier<?>> borrowedComponents = new HashMap<>();
    /**
     * 首次关闭未彻底完成且仍须重试清理的标记。
     */
    private boolean cleanupPending;

    /**
     * 用给定值初始化 <code>SaTokenRuntimeRegistration</code> 实例。
     *
     * @param context           <code>context</code> 字段
     * @param nativeConfig      <code>nativeConfig</code> 字段
     * @param security          <code>security</code> 字段
     * @param customLogic       <code>customLogic</code> 字段
     * @param customPermissions <code>customPermissions</code> 字段
     * @param subjectData       为认证主体提供角色、权限等业务数据的组件
     * @param dao               <code>dao</code> 字段
     * @param jsonTemplate      <code>jsonTemplate</code> 字段
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
     * 恢复原默认登录逻辑，同时保留独立认证域槽位的当前所有权。
     *
     * @param previous 需要恢复或判断是否被当前注册依赖的旧值
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
     * 恢复指定认证域的旧登录逻辑，原值为空时删除登记。
     *
     * @param realm    当前认证域名称
     * @param previous 需要恢复或判断是否被当前注册依赖的旧值
     */
    private static void restoreRealm(String realm, StpLogic previous) {
        if (Objects.isNull(previous)) {
            SaManager.removeStpLogic(realm);
        } else {
            SaManager.putStpLogic(previous);
        }
    }

    /**
     * 在不触发原生组件初始化的情况下读取权限槽位。
     *
     * @return 已安装的权限提供器，缺少时为空
     */
    private static StpInterface currentPermissions() {
        return component("stpInterface", StpInterface.class);
    }

    /**
     * 在不触发原生组件初始化的情况下读取存储槽位。
     *
     * @return 已安装的原生会话存储，缺少时为空
     */
    private static SaTokenDao currentDao() {
        return component("saTokenDao", SaTokenDao.class);
    }

    /**
     * 以给定类型读取 Sa-Token 管理器静态槽位中的原始值。
     *
     * @param <T>   原生组件类型
     * @param field 原生管理器中要读取的静态字段名称
     * @param type  该字段或回调结果对应的 Java 类型
     * @return 原生字段中现存的组件实例
     */
    private static <T> T component(String field, Class<T> type) {
        return type.cast(ReflectUtil.getStaticFieldValue(Objects.requireNonNull(
                ReflectUtil.getField(SaManager.class, field), "Missing Sa-Token component field: " + field)));
    }

    /**
     * 禁止在共享注册切换尚未完成时再次修改组件。
     *
     * @throws IllegalStateException 共享注册正在切换
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

    /**
     * 按配置装配认证逻辑、原生组件及其回滚记录。
     */
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
     * 在给定组件后记录可恢复旧值并更新槽位，已有同一实例时只登记依赖。
     *
     * @param <T>   当前槽位的组件类型
     * @param slot  当前被修改或复用的共享组件槽位
     * @param read  读取该槽位当前值的函数
     * @param write 写入或恢复槽位值的回调
     * @param value 当前操作准备写入、复用或规范化的值
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
     * 复用其他已安装的同一组件，追踪该槽位原有的所有者。
     *
     * @param <T>   被复用的组件类型
     * @param slot  当前被修改或复用的共享组件槽位
     * @param realm 当前认证域名称
     * @param read  读取该槽位当前值的函数
     * @param value 当前操作准备写入、复用或规范化的值
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
     * 登记当前组件变更以及退出时仍符合所有权条件的恢复动作。
     *
     * @param <T>      当前槽位的组件类型
     * @param slot     当前被修改或复用的共享组件槽位
     * @param realm    当前认证域名称
     * @param read     读取该槽位当前值的函数
     * @param write    写入或恢复槽位值的回调
     * @param previous 需要恢复或判断是否被当前注册依赖的旧值
     * @param value    当前操作准备写入、复用或规范化的值
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

    /**
     * 保存原生配置和安全属性以便退出后恢复。
     */
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
     * 安装指定认证域登录逻辑，并分别追踪默认和认证域槽位的变化。
     *
     * @param realm  当前认证域名称
     * @param create 构造当前认证域登录逻辑的函数
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
     * 在当前槽位仍指向选定逻辑时登记变更或复用依赖。
     *
     * @param slot     当前被修改或复用的共享组件槽位
     * @param realm    当前认证域名称
     * @param read     读取该槽位当前值的函数
     * @param write    写入或恢复槽位值的回调
     * @param previous 需要恢复或判断是否被当前注册依赖的旧值
     * @param expected 本次预计写入的原生登录逻辑
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
     * 检查当前注册是否仍依赖较早的运行时注册。
     *
     * @param previous 较早安装的运行时注册
     * @return 仍持有被依赖装配拥有的组件时返回 true
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

    /**
     * 按共享槽位顺序恢复原组件，并保留恢复失败的变更供重试。
     */
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
        if (failure != null) {
            throw (Error) failure;
        }
    }

    /**
     * 标识认证运行时中可安装和回滚的共享组件槽位。
     */
    private enum Slot {
        /**
         * Sa-Token 的默认登录逻辑。
         */
        DEFAULT_LOGIC,
        /**
         * 按标识区分的登录域组件槽位。
         */
        REALM,
        /**
         * 全局 Sa-Token 配置槽位。
         */
        CONFIG,
        /**
         * Sa-Token 请求上下文槽位。
         */
        CONTEXT,
        /**
         * 权限和角色数据提供槽位。
         */
        PERMISSIONS,
        /**
         * DDD4J 主体数据提供组件槽位。
         */
        SUBJECT_DATA,
        /**
         * 原生令牌及会话存储槽位。
         */
        DAO,
        /**
         * 原生 JSON 编解码组件槽位。
         */
        JSON
    }

    /**
     * 记录一次 Sa-Token 共享注册的原组件、新组件及恢复动作。
     */
    private static final class Change {

        /**
         * 本次安装操作占用的共享组件槽位。
         */
        private final Slot slot;
        /**
         * 当前认证逻辑或认证主体使用的认证域。
         */
        private final String realm;
        /**
         * 组件安装前保存的原对象。
         */
        private final Object previous;
        /**
         * 注册后由当前作用域持有的组件。
         */
        private final Object installed;
        /**
         * 在退出注册作用域时恢复原组件的动作。
         */
        private final Runnable restore;

        /**
         * 创建关联组件槽位、新旧值和恢复动作的变更记录。
         *
         * @param slot      当前被修改或复用的共享组件槽位
         * @param realm     当前认证域名称
         * @param previous  需要恢复或判断是否被当前注册依赖的旧值
         * @param installed 当前注册安装的组件
         * @param restore   撤销本次变更的动作
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
