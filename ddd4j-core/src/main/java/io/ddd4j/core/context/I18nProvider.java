package io.ddd4j.core.context;

/**
 * 国际化提供者接口（策略模式）。
 * <p>
 * 各框架适配层提供实现：
 * <ul>
 *   <li>Spring: 基于 MessageSource</li>
 *   <li>Quarkus: 基于 CDI + ResourceBundle</li>
 *   <li>Javalin/Guice: 基于 ResourceBundle</li>
 * </ul>
 * 默认实现返回原始 key。
 *
 * @author wandl
 */
public interface I18nProvider {

    /**
     * 默认实现：返回原始 key（不做国际化）
     */
    I18nProvider DEFAULT = new I18nProvider() {
        @Override
        public String getMessage(String key, Object... args) {
            if (args == null || args.length == 0) {
                return key;
            }
            // 简单占位符替换：{0}, {1}, ...
            String result = key;
            for (int i = 0; i < args.length; i++) {
                result = result.replace("{" + i + "}", String.valueOf(args[i]));
            }
            return result;
        }
    };

    /**
     * 获取国际化消息
     *
     * @param key  消息 key
     * @param args 格式化参数
     * @return 国际化后的消息
     */
    String getMessage(String key, Object... args);
}
