package io.ddd4j.data.mybatis.context;

import com.baomidou.mybatisplus.enhance.context.TenantContext;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import java.util.Objects;

/** 复用增强组件的租户作用域，将唯一租户来源桥接到 DDD4J 请求上下文。 */
public class Ddd4jTenantContext {

    private final TenantContext delegate = new TenantContext() {
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

    /** 从当前请求上下文清除租户。 */
    public void clear() {
        ThreadContext.remove(ContextConstants.TENANT_ID);
    }
    /**
     * 提供与 DDD4J 共享租户状态的增强组件上下文。
     * 该适配器的作用域与 {@link #open(Object)} 均由增强组件执行创建线程校验。
     *
     * @return 访问当前请求租户的增强组件适配器
     */
    public com.baomidou.mybatisplus.enhance.context.TenantContext asEnhanceContext() {
        return delegate;
    }

    /**
     * 在临时作用域内设置租户，由创建线程关闭作用域时恢复原值。
     *
     * @param tenantId 临时作用域的租户标识
     * @return 可关闭的租户作用域
     */
    public Scope open(Object tenantId) {
        return new Scope(delegate.open(tenantId));
    }

    /**
     * 复用增强组件的创建线程校验、租户恢复与幂等关闭逻辑。
     */
    public static final class Scope implements AutoCloseable {

        private final com.baomidou.mybatisplus.enhance.context.TenantContext.Scope delegateScope;

        private Scope(com.baomidou.mybatisplus.enhance.context.TenantContext.Scope scope) {
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
