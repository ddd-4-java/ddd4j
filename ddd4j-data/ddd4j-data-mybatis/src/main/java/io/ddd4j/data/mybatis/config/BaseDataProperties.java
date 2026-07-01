package io.ddd4j.data.mybatis.config;

import lombok.Data;

/**
 * ddd4j-data 配置属性。
 *
 * <p>SQL 日志请使用 MyBatis Plus 官方配置：
 * <pre>
 * mybatis-plus:
 *   configuration:
 *     log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Data
// @ConfigurationProperties(prefix = "base-data")
public class BaseDataProperties {
}
