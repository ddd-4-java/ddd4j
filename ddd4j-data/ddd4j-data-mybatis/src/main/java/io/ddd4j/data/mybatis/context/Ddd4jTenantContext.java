package io.ddd4j.data.mybatis.context;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;

import java.util.Objects;

/**
 * 复用增强组件的租户作用域，将唯一租户来源桥接到 DDD4J 请求上下文。
 */
public class Ddd4jTenantContext {
    /**
     * 上游 ORM 增强组件提供的租户上下文委托。
     */
    private final org.apache.ibatis.enhance.context.TenantContext delegate = new org.apache.ibatis.enhance.context.TenantContext() {
        @Override
        public Object getCurrentTenantId() {
            return Ddd4jTenantContext.this.getCurrentTenantId();
        }

        @Override
        public void setCurrentTenantId(Object tenantId) {
            Ddd4jTenantContext.this.setCurrentTenantId(tenantId);
        }

        @Override
        public void clear() {
            Ddd4jTenantContext.this.clear();
        }
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

    /**
     * 从当前请求上下文清除租户。
     */
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

    /**
     * 兼容既有 Ddd4jTenantContext.Scope 的薄包装。
     */
    public static final class Scope implements AutoCloseable {
        /**
         * 上游租户作用域，关闭时恢复进入前的租户状态。
         */
        private final org.apache.ibatis.enhance.context.TenantContext.Scope delegateScope;

        /**
         * 包装上游租户作用域以便随 DDD4J 上下文关闭。
         *
         * @param scope 上游 ORM 租户上下文作用域
         */
        private Scope(org.apache.ibatis.enhance.context.TenantContext.Scope scope) {
            this.delegateScope = scope;
        }

        /**
         * 在创建线程恢复原租户；同线程重复关闭不再改变上下文。
         *
         * @throws IllegalStateException 由其他线程关闭，包括已经关闭的作用域
         */
        @Override
        public void close() {
            delegateScope.close();
        }
    }
}
