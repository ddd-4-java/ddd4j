package io.ddd4j.boot.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@ConfigurationProperties(prefix = "base-core")
public class BaseCoreProperties {
    // 日期格式
    private String datePattern = "yyyy-MM-dd";
    // 日期时间格式
    private String dateTimePattern = "yyyy-MM-dd HH:mm:ss";
    // 时间格式
    private String timePattern = "HH:mm:ss";
}