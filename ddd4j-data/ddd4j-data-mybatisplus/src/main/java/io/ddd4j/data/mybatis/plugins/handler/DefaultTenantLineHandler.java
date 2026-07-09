package io.ddd4j.data.mybatis.plugins.handler;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.StringValue;

/**
 * 默认租户处理器：读取系统属性 {@code ddd4j.tenant.id} 作为租户 ID
 */
public class DefaultTenantLineHandler implements TenantLineHandler {

    public static final String DEFAULT_TENANT_ID_KEY = "ddd4j.tenant.id";
    public static final String DEFAULT_TENANT_COLUMN = "tenant_id";

    @Override
    public Expression getTenantId() {
        String tenantId = System.getProperty(DEFAULT_TENANT_ID_KEY, "0");
        try {
            return new StringValue(tenantId);
        } catch (Exception e) {
            return new NullValue();
        }
    }

    @Override
    public String getTenantIdColumn() {
        return DEFAULT_TENANT_COLUMN;
    }

    @Override
    public boolean ignoreTable(String tableName) {
        // 系统表跳过
        return tableName.startsWith("qrtz_") || tableName.startsWith("act_") || tableName.startsWith("flw_");
    }
}