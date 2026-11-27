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
import com.baomidou.mybatisplus.enhance.datascope.handler.SqlDataScopeHandler;
import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.baomidou.mybatisplus.enhance.crypto.handler.EncryptedFieldHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.enhance.plugins.inner.QueryResultCallbackInterceptor;
import io.ddd4j.data.mybatis.repository.AggregateQueryResultFiller;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/** 框架中立的 MyBatis-Plus 增强链装配器；安全策略由应用显式提供。 */
public final class Ddd4jMybatisConfigurer {
    /** SQL \u79df\u6237\u9694\u79bb\u4f7f\u7528\u7684\u5217\u540d\u79f0\u3002 */
    private String tenantColumn;
    /** \u5224\u65ad\u67d0\u5f20\u8868\u662f\u5426\u5e94\u8df3\u8fc7\u79df\u6237\u8fc7\u6ee4\u7684\u89c4\u5219\u3002 */
    private Predicate<String> ignoredTable;
    /** \u4e3a\u6570\u636e\u6743\u9650\u6761\u4ef6\u63d0\u4f9b\u5f53\u524d\u8bbf\u95ee\u8303\u56f4\u7684\u7ec4\u4ef6\u3002 */
    private Function<String, String> scopeProvider;
    /** \u751f\u6210 MyBatis-Plus \u6570\u636e\u6743\u9650\u6761\u4ef6\u7684\u5904\u7406\u5668\u3002 */
    private MultiDataPermissionHandler permissionHandler;
    /** ORM \u9002\u914d\u5668\u4f7f\u7528\u7684\u5b57\u6bb5\u52a0\u89e3\u5bc6\u5904\u7406\u5668\u3002 */
    private EncryptedFieldHandler crypto;
    /** \u5bf9\u6301\u4e45\u5316\u5b57\u6bb5\u6267\u884c\u7b7e\u540d\u76f8\u5173\u5904\u7406\u7684\u7ec4\u4ef6\u3002 */
    private boolean signature;

    /**
     * 配置租户列与共享表白名单。
     *
     * @param column 用于租户隔离的列名
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
