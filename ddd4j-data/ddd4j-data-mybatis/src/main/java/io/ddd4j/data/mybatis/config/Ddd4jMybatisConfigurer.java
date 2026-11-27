package io.ddd4j.data.mybatis.config;

import org.apache.ibatis.enhance.plugins.MybatisEnhanceInterceptor;
import org.apache.ibatis.enhance.crypto.interceptor.DataEncryptionInnerInterceptor;
import org.apache.ibatis.enhance.crypto.interceptor.DataSignatureInnerInterceptor;
import org.apache.ibatis.enhance.crypto.interceptor.DataDecryptionInnerInterceptor;
import org.apache.ibatis.enhance.crypto.handler.DefaultDataSignatureHandler;
import org.apache.ibatis.enhance.plugins.inner.SqlObservationInnerInterceptor;
import org.apache.ibatis.enhance.crypto.handler.EncryptedFieldHandler;
import java.util.Objects;

/** 原生 MyBatis 增强链装配，保留显式 Mapper/XML SQL 模型。 */
public final class Ddd4jMybatisConfigurer {
    /** ORM \u9002\u914d\u5668\u4f7f\u7528\u7684\u5b57\u6bb5\u52a0\u89e3\u5bc6\u5904\u7406\u5668\u3002 */
    private EncryptedFieldHandler crypto;
    /** \u5bf9\u6301\u4e45\u5316\u5b57\u6bb5\u6267\u884c\u7b7e\u540d\u76f8\u5173\u5904\u7406\u7684\u7ec4\u4ef6\u3002 */
    private boolean signature;
    /** SQL \u79df\u6237\u9694\u79bb\u4f7f\u7528\u7684\u5217\u540d\u79f0\u3002 */
    private String tenantColumn;
    /** \u4e0d\u5e94\u7528\u79df\u6237\u9694\u79bb\u89c4\u5219\u7684\u8868\u96c6\u5408\u3002 */
    private java.util.function.Predicate<String> ignored = table -> false;

    /**
     * 设置租户过滤，启用后缺失租户即终止执行。
     *
     * @param column 租户字段名
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
