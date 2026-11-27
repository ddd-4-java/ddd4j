package io.ddd4j.data.mybatis.context;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.context.ThreadContext;
import java.util.Objects;

/** 复用增强组件的租户作用域，将唯一租户来源桥接到 DDD4J 请求上下文。 */
public class Ddd4jTenantContext {
    /** \u4e0a\u6e38 ORM \u589e\u5f3a\u7ec4\u4ef6\u63d0\u4f9b\u7684\u79df\u6237\u4e0a\u4e0b\u6587\u59d4\u6258\u3002 */
    private final org.apache.ibatis.enhance.context.TenantContext delegate = new org.apache.ibatis.enhance.context.TenantContext() {
        @Override public Object getCurrentTenantId() { return Ddd4jTenantContext.this.getCurrentTenantId(); }
        @Override public void setCurrentTenantId(Object tenantId) { Ddd4jTenantContext.this.setCurrentTenantId(tenantId); }
        @Override public void clear() { Ddd4jTenantContext.this.clear(); }
    };

    /**
     * 读取当前请求的租户。
     *
     * @return 当前租户标识，无租户时返回 null
     */
    public Object getCurrentTenantId() {
        return ThreadContext.get(ContextConstants.TENANT_ID);
    }

    /**
     * 设置当前请求的租户。
     *
     * @param tenantId 租户标识，传入 null 时清除当前租户
     */
    public void setCurrentTenantId(Object tenantId) {
        if (Objects.isNull(tenantId)) {
            clear();
        } else {
            ThreadContext.set(ContextConstants.TENANT_ID, tenantId);
        }
    }

    /** 从当前请求上下文清除租户。 */
    public void clear() {
        ThreadContext.remove(ContextConstants.TENANT_ID);
    }

    /**
     * 提供与 DDD4J 共享租户状态的增强组件上下文。
     *
     * @return 访问当前请求租户的增强组件适配器
     */
    public org.apache.ibatis.enhance.context.TenantContext asEnhanceContext() {
        return delegate;
    }

    /**
     * 在临时作用域内设置租户，关闭作用域时恢复原值。
     *
     * @param tenantId 临时作用域的租户标识
     * @return 可关闭的租户作用域
     */
    public Scope open(Object tenantId) {
        return new Scope(delegate.open(tenantId));
    }

    /** 兼容既有 Ddd4jTenantContext.Scope 的薄包装。 */
    public static final class Scope implements AutoCloseable {
        /** \u4e0a\u6e38\u79df\u6237\u4f5c\u7528\u57df\uff0c\u5173\u95ed\u65f6\u6062\u590d\u8fdb\u5165\u524d\u7684\u79df\u6237\u72b6\u6001\u3002 */
        private final org.apache.ibatis.enhance.context.TenantContext.Scope delegateScope;

        /**
         * \u5305\u88c5\u4e0a\u6e38\u79df\u6237\u4f5c\u7528\u57df\u4ee5\u4fbf\u968f DDD4J \u4e0a\u4e0b\u6587\u5173\u95ed\u3002
         * @param scope \u4e0a\u6e38 ORM \u79df\u6237\u4e0a\u4e0b\u6587\u4f5c\u7528\u57df
         */
        private Scope(org.apache.ibatis.enhance.context.TenantContext.Scope scope) {
            this.delegateScope = scope;
        }

        /**
         * \u5728\u521b\u5efa\u7ebf\u7a0b\u6062\u590d\u539f\u79df\u6237\uff1b\u540c\u7ebf\u7a0b\u91cd\u590d\u5173\u95ed\u4e0d\u518d\u6539\u53d8\u4e0a\u4e0b\u6587\u3002
         * @throws IllegalStateException \u7531\u5176\u4ed6\u7ebf\u7a0b\u5173\u95ed\uff0c\u5305\u62ec\u5df2\u7ecf\u5173\u95ed\u7684\u4f5c\u7528\u57df
         */
        @Override
        public void close() {
            delegateScope.close();
        }
    }
}
