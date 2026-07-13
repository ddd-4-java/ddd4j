package io.ddd4j.extension.excel.config;

/**
 * ddd4j-excel 核心对象工厂（纯 Java，无 Spring 依赖）。
 *
 * <p>参考 {@code BaseMonitorConfig} / {@code DefaultJacksonAutoConfiguration} 的项目惯例：
 * 扩展模块本身不依赖 Spring 容器，由上层框架（Spring/Quarkus/Javalin）完成装配。
 *
 * <p>当前仅暴露默认 {@link ExcelProperties}，后续可在本类追加：
 * <ul>
 *   <li>全局 Converter 注册</li>
 *   <li>ExcelSchema 反射缓存</li>
 *   <li>默认 WriteHandler 链</li>
 * </ul>
 *
 * <h3>Spring 装配示例</h3>
 * <pre>{@code
 * @Configuration
 * public class ExcelSpringConfig {
 *     @Bean
 *     public ExcelProperties excelProperties() {
 *         return new ExcelAutoConfiguration().excelProperties();
 *     }
 * }
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class ExcelAutoConfiguration {

    /**
     * 默认配置（与 yml 默认值一致）。
     *
     * @return 默认 {@link ExcelProperties}
     */
    public ExcelProperties excelProperties() {
        return new ExcelProperties();
    }
}
