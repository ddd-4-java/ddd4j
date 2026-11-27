package io.ddd4j.web.testkit.validation;

import io.github.easy4j.validation.constraints.RequiresDataPermissions;
import io.github.easy4j.validation.provider.DataScopeProvider;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 通过 Spring 原生工厂验证 Easy4j 数据权限约束及 Provider 隔离。 */
class DataPermissionSpringIntegrationTest {
    /** \u9a8c\u8bc1<code>validator.validate(new Request("allowed", "valid"))</code>\u4e3a\u7a7a\uff0c<code>v.getPropertyPath().toString()</code> \u7b49\u4e8e <code>"orderId"</code>\uff0c<code>v.getPropertyPath().toString()</code> \u7b49\u4e8e <code>"name"</code>\u3002 */
    @Test
    void nativeSpringFactoryInjectsApplicationProviderAndKeepsStandardConstraints() {
        try (AnnotationConfigApplicationContext context = context((type, value) ->
                "order".equals(type) && "allowed".equals(value))) {
            LocalValidatorFactoryBean validator = context.getBean(LocalValidatorFactoryBean.class);
            assertThat(validator.validate(new Request("allowed", "valid"))).isEmpty();
            assertThat(validator.validate(new Request("denied", "valid")))
                    .singleElement().satisfies(v -> {
                        assertThat(v.getPropertyPath().toString()).isEqualTo("orderId");
                        assertThat(v.getMessage()).isEqualTo("订单不可见");
                    });
            assertThat(validator.validate(new Request(null, "valid"))).hasSize(1);
            assertThat(validator.validate(new Request("allowed", "")))
                    .singleElement().satisfies(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("name"));
        }
    }

    /** \u9a8c\u8bc1<code>context.getBeansOfType(DataScopeProvider.class)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void missingProviderFailsInsteadOfGrantingAccess() {
        try (AnnotationConfigApplicationContext context = context()) {
            assertThat(context.getBeansOfType(DataScopeProvider.class)).isEmpty();
            LocalValidatorFactoryBean validator = context.getBean(LocalValidatorFactoryBean.class);
            assertThatThrownBy(() -> validator.validate(new Request("anything", "valid")))
                    .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class);
        }
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>context()</code>\u3001<code>context.getBean()</code>\u3001<code>validator.validate()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void ambiguousProvidersFailInsteadOfSelectingAnArbitraryPolicy() {
        try (AnnotationConfigApplicationContext context = context((type, value) -> true, (type, value) -> false)) {
            LocalValidatorFactoryBean validator = context.getBean(LocalValidatorFactoryBean.class);
            assertThatThrownBy(() -> validator.validate(new Request("anything", "valid")))
                    .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class);
        }
    }

    /** \u9a8c\u8bc1<code>allow.getBean(LocalValidatorFactoryBean.class).validate(request)</code>\u4e3a\u7a7a\uff0c<code>deny.getBean(LocalValidatorFactoryBean.class).validate(request)</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\u3002 */
    @Test
    void twoApplicationContextsKeepTheirOwnProvider() {
        try (AnnotationConfigApplicationContext allow = context((type, value) -> true);
             AnnotationConfigApplicationContext deny = context((type, value) -> false)) {
            Request request = new Request("order-1", "valid");
            assertThat(allow.getBean(LocalValidatorFactoryBean.class).validate(request)).isEmpty();
            assertThat(deny.getBean(LocalValidatorFactoryBean.class).validate(request)).hasSize(1);
            assertThat(allow.getBean(LocalValidatorFactoryBean.class).validate(request)).isEmpty();
        }
    }

    /**
     * \u8c03\u7528 <code>context.registerBean()</code>\u3001<code>validator.setMessageInterpolator()</code>\u3001<code>context.refresh()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param providers \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>DataScopeProvider[]</code> \u53c2\u6570 <code>providers</code>
     * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>validator</code>\u3001<code>context</code>
     */
    private AnnotationConfigApplicationContext context(DataScopeProvider... providers) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        for (int i = 0; i < providers.length; i++) {
            DataScopeProvider provider = providers[i];
            context.registerBean("permissionPolicy" + i, DataScopeProvider.class, () -> provider);
        }
        context.registerBean("validator", LocalValidatorFactoryBean.class, () -> {
            LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
            validator.setMessageInterpolator(new ParameterMessageInterpolator());
            return validator;
        });
        context.refresh();
        return context;
    }

    /** \u5c06 <code>orderId</code>\u3001<code>name</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    private static class Request {
        /** \u8ba2\u5355\u6807\u8bc6\u3002 */
        @RequiresDataPermissions(dataType = "order", message = "订单不可见")
        private final String orderId;
        /** \u5f53\u524d\u6d4b\u8bd5\u6a21\u578b\u7528\u4e8e\u5c55\u793a\u548c\u65ad\u8a00\u7684\u540d\u79f0\u3002 */
        @NotBlank
        private final String name;

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>Request</code> \u5b9e\u4f8b\u3002
         * @param orderId \u8ba2\u5355\u6807\u8bc6
         * @param name <code>name</code> \u5b57\u6bb5
         */
        Request(String orderId, String name) {
            this.orderId = orderId;
            this.name = name;
        }
    }
}
