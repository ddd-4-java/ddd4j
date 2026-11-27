package io.ddd4j.data.mybatis.config;

import com.baomidou.mybatisplus.enhance.context.TenantContext;
import com.baomidou.mybatisplus.enhance.crypto.handler.DefaultDataSignatureHandler;
import com.baomidou.mybatisplus.enhance.crypto.handler.EncryptedFieldHandler;
import com.baomidou.mybatisplus.enhance.datascope.handler.SqlDataScopeHandler;
import com.baomidou.mybatisplus.enhance.plugins.MybatisPlusEnhanceInterceptor;
import com.baomidou.mybatisplus.enhance.plugins.inner.*;
import com.baomidou.mybatisplus.enhance.tenant.DefaultTenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.*;
import io.ddd4j.data.mybatis.context.Ddd4jTenantContext;
import io.ddd4j.data.mybatis.repository.AggregateQueryResultFiller;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 框架中立的 MyBatis-Plus 增强链装配器；安全策略由应用显式提供。
 */
public final class Ddd4jMybatisConfigurer {
    /**
     * SQL 租户隔离使用的列名称。
     */
    private String tenantColumn;
    /**
     * 判断某张表是否应跳过租户过滤的规则。
     */
    private Predicate<String> ignoredTable;
    /**
     * 为数据权限条件提供当前访问范围的组件。
     */
    private Function<String, String> scopeProvider;
    /**
     * 生成 MyBatis-Plus 数据权限条件的处理器。
     */
    private MultiDataPermissionHandler permissionHandler;
    /**
     * ORM 适配器使用的字段加解密处理器。
     */
    private EncryptedFieldHandler crypto;
    /**
     * 对持久化字段执行签名相关处理的组件。
     */
    private boolean signature;

    /**
     * 配置租户列与共享表白名单。
     *
     * @param column  用于租户隔离的列名
     * @param ignored 判断表是否可以跳过租户过滤的谓词
     * @return 当前装配器
     */
    public Ddd4jMybatisConfigurer tenant(String column, Predicate<String> ignored) {
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
    public Ddd4jMybatisConfigurer dataScope(Function<String, String> provider) {
        if (Objects.nonNull(permissionHandler)) {
            throw new IllegalStateException("dataScope and dataPermission are mutually exclusive");
        }
        this.scopeProvider = Objects.requireNonNull(provider, "provider");
        return this;
    }

    /**
     * 配置官方多表权限处理器。
     *
     * @param handler 应用按语句和表提供的权限表达式处理器
     * @return 当前装配器
     */
    public Ddd4jMybatisConfigurer dataPermission(MultiDataPermissionHandler handler) {
        if (Objects.nonNull(scopeProvider)) {
            throw new IllegalStateException("dataScope and dataPermission are mutually exclusive");
        }
        this.permissionHandler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /**
     * 配置字段密码运算；注解决定需要保护的字段。
     *
     * @param handler 字段加解密处理器
     * @return 当前装配器
     */
    public Ddd4jMybatisConfigurer encryption(EncryptedFieldHandler handler) {
        this.crypto = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /**
     * 开启表签名与查询验签；必须同时配置密码处理器。
     *
     * @param enabled 是否启用签名与验签
     * @return 当前装配器
     */
    public Ddd4jMybatisConfigurer signature(boolean enabled) {
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
            chain.addInnerInterceptor(new DataPermissionInterceptor(new SqlDataScopeHandler(scopeProvider)));
        } else if (Objects.nonNull(permissionHandler)) {
            chain.addInnerInterceptor(new DataPermissionInterceptor(permissionHandler));
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
        chain.addInnerInterceptor(new QueryResultCallbackInterceptor(new AggregateQueryResultFiller()::fill));
        chain.addInnerInterceptor(new SqlObservationInnerInterceptor());
        return chain;
    }
}
