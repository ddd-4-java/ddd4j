package io.ddd4j.boot.data.config;

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
 * @author wandl
 * @since 3.4.x
 */
@Data
// @ConfigurationProperties(prefix = "base-data")
public class BaseDataProperties {
}
