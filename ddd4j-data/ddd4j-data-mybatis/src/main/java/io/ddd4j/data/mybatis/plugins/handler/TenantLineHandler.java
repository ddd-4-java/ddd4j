package io.ddd4j.data.mybatis.plugins.handler;

/**
 * 租户处理器 SPI（零 MyBatis-Plus 依赖）。
 *
 * <p>提供租户 ID 和租户列名，由 {@link io.ddd4j.data.mybatis.plugins.inner.DefaultTenantLineInnerInterceptor} 使用。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface TenantLineHandler {

    /**
     * 获取当前租户 ID。
     */
    Object getTenantId();

    /**
     * 获取租户列名（默认 tenant_id）。
     */
    String getTenantIdColumn();

    /**
     * 判断是否跳过该表（如系统表）。
     */
    boolean ignoreTable(String tableName);
}
