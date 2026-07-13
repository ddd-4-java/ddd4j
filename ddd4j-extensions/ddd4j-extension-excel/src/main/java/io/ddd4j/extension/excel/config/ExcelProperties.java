package io.ddd4j.extension.excel.config;

import lombok.Data;

/**
 * ddd4j-excel 配置属性。
 *
 * <p>纯 POJO，在 Spring 环境下可启用 {@code @ConfigurationProperties(prefix = "ddd4j.excel")}
 * 完成自动绑定；在非 Spring 环境（Quarkus/Javalin）可手动实例化。
 *
 * <p>配置示例（application.yml）：
 * <pre>{@code
 * ddd4j:
 *   excel:
 *     batch-size: 1000
 *     max-upload-mb: 50
 *     charset: UTF-8
 *     style:
 *       default-border: true
 *       auto-size-column: true
 *       header-row-height: 600
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
// @ConfigurationProperties(prefix = "ddd4j.excel")
@Data
public class ExcelProperties {

    /**
     * 配置前缀。
     */
    public static final String PREFIX = "ddd4j.excel";

    /**
     * 监听器批量入库阈值，默认 1000 行。
     */
    private int batchSize = 1000;

    /**
     * Web 上传单文件大小上限（MB），默认 50。
     */
    private int maxUploadMB = 50;

    /**
     * 字符编码，默认 UTF-8（影响 CSV 与文件名编码）。
     */
    private String charset = "UTF-8";

    /**
     * 样式相关配置。
     */
    private Style style = new Style();

    @Data
    public static class Style {

        /**
         * 是否为单元格启用默认细边框。
         */
        private boolean defaultBorder = true;

        /**
         * 是否自动适配列宽（基于内容长度）。
         */
        private boolean autoSizeColumn = true;

        /**
         * 表头行高（单位：1/20 磅，即 short）。
         * <p>例如 600 表示 30 磅；默认 600。
         */
        private short headerRowHeight = 600;
    }
}
