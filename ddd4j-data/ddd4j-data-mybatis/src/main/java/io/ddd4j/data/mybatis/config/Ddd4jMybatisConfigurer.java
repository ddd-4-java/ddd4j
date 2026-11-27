package io.ddd4j.data.mybatis.config;

import org.apache.ibatis.enhance.crypto.handler.DefaultDataSignatureHandler;
import org.apache.ibatis.enhance.crypto.handler.EncryptedFieldHandler;
import org.apache.ibatis.enhance.crypto.interceptor.DataDecryptionInnerInterceptor;
import org.apache.ibatis.enhance.crypto.interceptor.DataEncryptionInnerInterceptor;
import org.apache.ibatis.enhance.crypto.interceptor.DataSignatureInnerInterceptor;
import org.apache.ibatis.enhance.plugins.MybatisEnhanceInterceptor;
import org.apache.ibatis.enhance.plugins.inner.SqlObservationInnerInterceptor;

import java.util.Objects;

/**
 * 原生 MyBatis 增强链装配，保留显式 Mapper/XML SQL 模型。
 */
public final class Ddd4jMybatisConfigurer {
    /**
     * ORM 适配器使用的字段加解密处理器。
     */
    private EncryptedFieldHandler crypto;
    /**
     * 对持久化字段执行签名相关处理的组件。
     */
    private boolean signature;
    /**
     * SQL 租户隔离使用的列名称。
     */
    private String tenantColumn;
    /**
     * 不应用租户隔离规则的表集合。
     */
    private java.util.function.Predicate<String> ignored = table -> false;

    /**
     * 设置租户过滤，启用后缺失租户即终止执行。
     *
     * @param column  租户字段名
     * @param ignored 判断表是否免于租户过滤的条件
     * @return 当前装配器
     */
    public Ddd4jMybatisConfigurer tenant(String column, java.util.function.Predicate<String> ignored) {
        this.tenantColumn = Objects.requireNonNull(column, "column");
        this.ignored = Objects.requireNonNull(ignored, "ignored");
        return this;
    }

    /**
     * 配置增强组件的字段加解密处理器。
     *
     * @param handler 用于字段加解密的处理器
     * @return 当前装配器
     */
    public Ddd4jMybatisConfigurer encryption(EncryptedFieldHandler handler) {
        this.crypto = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /**
     * 设置是否开启表签名和查询验签。
     *
     * @param enabled 是否开启签名和验签
     * @return 当前装配器
     */
    public Ddd4jMybatisConfigurer signature(boolean enabled) {
        this.signature = enabled;
        return this;
    }

    /**
     * 构造按加密、签名、验签、解密和观测顺序运行的增强链。
     *
     * @return 按当前配置装配的增强拦截器
     */
    public MybatisEnhanceInterceptor build() {
        MybatisEnhanceInterceptor chain = new MybatisEnhanceInterceptor();
        if (Objects.nonNull(tenantColumn)) {
            chain.addInterceptor(new org.apache.ibatis.enhance.plugins.inner.TenantLineInnerInterceptor(tenantColumn, ignored,
                    () -> io.ddd4j.core.context.ThreadContext.get(io.ddd4j.core.constant.ContextConstants.TENANT_ID)));
        }
        if (Objects.nonNull(crypto)) {
            chain.addInterceptor(new DataEncryptionInnerInterceptor(crypto));
        }
        if (signature) {
            chain.addInterceptor(new DataSignatureInnerInterceptor(new DefaultDataSignatureHandler(
                    Objects.requireNonNull(crypto, "signature requires encryption handler")), true, true));
        }
        if (Objects.nonNull(crypto)) {
            chain.addInterceptor(new DataDecryptionInnerInterceptor(crypto));
        }
        chain.addInterceptor(new SqlObservationInnerInterceptor());
        return chain;
    }
}
