package io.ddd4j.data.mybatis.config;

import com.baomidou.mybatisplus.enhance.plugins.MybatisPlusEnhanceInterceptor;
import com.baomidou.mybatisplus.enhance.plugins.inner.DataEncryptionInnerInterceptor;
import com.baomidou.mybatisplus.enhance.plugins.inner.DataDecryptionInnerInterceptor;
import com.baomidou.mybatisplus.enhance.plugins.inner.DataSignatureInnerInterceptor;
import com.baomidou.mybatisplus.enhance.plugins.inner.SqlObservationInnerInterceptor;
import com.baomidou.mybatisplus.enhance.crypto.handler.DefaultDataSignatureHandler;
import com.baomidou.mybatisplus.enhance.context.TenantContext;
import com.baomidou.mybatisplus.enhance.tenant.DefaultTenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import io.ddd4j.data.mybatis.context.Ddd4jTenantContext;
import io.ddd4j.data.mybatis.plugins.DataScopeProvider;
import com.baomidou.mybatisplus.enhance.crypto.handler.EncryptedFieldHandler;
import io.ddd4j.data.mybatis.plugins.inner.DataPermissionInnerInterceptor;
import io.ddd4j.data.mybatis.plugins.inner.Ddd4jAggregateFillInnerInterceptor;

import java.util.Objects;
import java.util.function.Predicate;

/** 框架中立的 MyBatis-Plus 增强链装配器；安全策略由应用显式提供。 */
public final class Ddd4jMybatisPlusConfigurer {
    private String tenantColumn;
    private Predicate<String> ignoredTable;
    private DataScopeProvider scopeProvider;
    private EncryptedFieldHandler crypto;
    private boolean signature;

    /**
     * 配置租户列与共享表白名单。
     *
     * @param column 用于租户隔离的列名
     * @param ignored 判断表是否可以跳过租户过滤的谓词
     * @return 当前装配器
     */
    public Ddd4jMybatisPlusConfigurer tenant(String column, Predicate<String> ignored) {
        this.tenantColumn = Objects.requireNonNull(column, "column");
        this.ignoredTable = Objects.requireNonNull(ignored, "ignored");
        return this;
    }

    /**
     * 配置 Mapper 数据范围策略。
     *
     * @param provider 提供查询数据范围的策略
     * @return 当前装配器
     */
    public Ddd4jMybatisPlusConfigurer dataScope(DataScopeProvider provider) {
        this.scopeProvider = Objects.requireNonNull(provider, "provider");
        return this;
    }

    /**
     * 配置字段密码运算；注解决定需要保护的字段。
     *
     * @param handler 字段加解密处理器
     * @return 当前装配器
     */
    public Ddd4jMybatisPlusConfigurer encryption(EncryptedFieldHandler handler) {
        this.crypto = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /**
     * 开启表签名与查询验签；必须同时配置密码处理器。
     *
     * @param enabled 是否启用签名与验签
     * @return 当前装配器
     */
    public Ddd4jMybatisPlusConfigurer signature(boolean enabled) {
        this.signature = enabled;
        return this;
    }

    /**
     * 创建独立增强链，调用方应只注册一个外层插件。
     *
     * @return 按当前设置创建的增强链
     */
    public MybatisPlusEnhanceInterceptor build() {
        MybatisPlusEnhanceInterceptor chain = new MybatisPlusEnhanceInterceptor();
        if (Objects.nonNull(tenantColumn)) {
            TenantContext context = new Ddd4jTenantContext().asEnhanceContext();
            chain.addInnerInterceptor(new TenantLineInnerInterceptor(
                    new DefaultTenantLineHandler(context, tenantColumn, ignoredTable)));
        }
        if (Objects.nonNull(scopeProvider)) {
            chain.addInnerInterceptor(new DataPermissionInnerInterceptor(scopeProvider));
        }
        chain.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        chain.addInnerInterceptor(new BlockAttackInnerInterceptor());
        // 分页放在 SQL 权限改写之后，COUNT 与数据查询共用隔离条件。
        chain.addInnerInterceptor(new PaginationInnerInterceptor());
        if (Objects.nonNull(crypto)) {
            chain.addInnerInterceptor(new DataEncryptionInnerInterceptor(crypto));
        }
        if (signature) {
            chain.addInnerInterceptor(new DataSignatureInnerInterceptor(
                    new DefaultDataSignatureHandler(Objects.requireNonNull(crypto, "signature requires encryption handler")), true, true));
        }
        if (Objects.nonNull(crypto)) {
            chain.addInnerInterceptor(new DataDecryptionInnerInterceptor(crypto));
        }
        chain.addInnerInterceptor(new Ddd4jAggregateFillInnerInterceptor());
        chain.addInnerInterceptor(new SqlObservationInnerInterceptor());
        return chain;
    }
}
