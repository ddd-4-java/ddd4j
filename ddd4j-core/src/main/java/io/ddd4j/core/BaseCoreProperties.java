package io.ddd4j.core;

import lombok.Data;

/**
 * 核心模块配置项（绑定由上层 Boot 或 {@code @Value} 完成）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class BaseCoreProperties {
    /** \u9ed8\u8ba4\u65e5\u671f\u8f93\u5165\u8f93\u51fa\u683c\u5f0f\u3002 */
    private String datePattern = "yyyy-MM-dd";
    /** \u9ed8\u8ba4\u65e5\u671f\u65f6\u95f4\u8f93\u5165\u8f93\u51fa\u683c\u5f0f\u3002 */
    private String dateTimePattern = "yyyy-MM-dd HH:mm:ss";
    /** \u9ed8\u8ba4\u65f6\u523b\u8f93\u5165\u8f93\u51fa\u683c\u5f0f\u3002 */
    private String timePattern = "HH:mm:ss";
}
