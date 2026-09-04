package io.ddd4j.core.config;

import lombok.Data;

/**
 * 核心模块配置项（绑定由上层 Boot 或 {@code @Value} 完成）。
 */
@Data
public class BaseCoreProperties {
    private String datePattern = "yyyy-MM-dd";
    private String dateTimePattern = "yyyy-MM-dd HH:mm:ss";
    private String timePattern = "HH:mm:ss";
}
