package io.ddd4j.data.mybatis.plugins.handler;

/**
 * 默认租户处理器（零 MyBatis-Plus 依赖）。
 *
 * <p>读取系统属性 {@code ddd4j.tenant.id} 作为租户 ID。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class DefaultTenantLineHandler implements TenantLineHandler {

    public static final String DEFAULT_TENANT_ID_KEY = "ddd4j.tenant.id";
    public static final String DEFAULT_TENANT_COLUMN = "tenant_id";

    @Override
    public Object getTenantId() {
        return System.getProperty(DEFAULT_TENANT_ID_KEY, "0");
    }

    @Override
    public String getTenantIdColumn() {
        return DEFAULT_TENANT_COLUMN;
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return tableName.startsWith("qrtz_") || tableName.startsWith("act_") || tableName.startsWith("flw_");
    }
}
