package io.ddd4j.core;


/**
 * 当前活跃环境配置文件管理器。
 * <p>
 * 通过 {@link java.util.function.Supplier} 获取当前应用的所有活跃 Profile 名称，
 * 并提供便捷方法获取第一个活跃 Profile。
 * <p>
 * 各框架适配层（Spring / Quarkus）在启动时注入对应的 Profile 提供者。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class ProfileManager {

    private final java.util.function.Supplier<String[]> activeProfilesSupplier;

    /**
     * 构造 ProfileManager。
     *
     * @param activeProfilesSupplier 活跃 Profile 名称数组的提供者
     */
    public ProfileManager(java.util.function.Supplier<String[]> activeProfilesSupplier) {
        this.activeProfilesSupplier = activeProfilesSupplier;
    }

    /**
     * 获取第一个活跃的 Profile 名称。
     *
     * @return 第一个活跃 Profile 名称，如果没有则返回 null
     */
    public String getOneActive() {
        for (String profileName : activeProfilesSupplier.get()) {
            return profileName;
        }
        return null;
    }
}
