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

/**
 * 通过 Spring 原生工厂验证 Easy4j 数据权限约束及 Provider 隔离。
 */
class DataPermissionSpringIntegrationTest {
    /**
     * 验证<code>validator.validate(new Request("allowed", "valid"))</code>为空，<code>v.getPropertyPath().toString()</code> 等于 <code>"orderId"</code>，<code>v.getPropertyPath().toString()</code> 等于 <code>"name"</code>。
     */
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

    /**
     * 验证<code>context.getBeansOfType(DataScopeProvider.class)</code>为空。
     */
    @Test
    void missingProviderFailsInsteadOfGrantingAccess() {
        try (AnnotationConfigApplicationContext context = context()) {
            assertThat(context.getBeansOfType(DataScopeProvider.class)).isEmpty();
            LocalValidatorFactoryBean validator = context.getBean(LocalValidatorFactoryBean.class);
            assertThatThrownBy(() -> validator.validate(new Request("anything", "valid")))
                    .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class);
        }
    }

    /**
     * 在测试框架中执行 <code>context()</code>、<code>context.getBean()</code>、<code>validator.validate()</code>，验证该场景。
     */
    @Test
    void ambiguousProvidersFailInsteadOfSelectingAnArbitraryPolicy() {
        try (AnnotationConfigApplicationContext context = context((type, value) -> true, (type, value) -> false)) {
            LocalValidatorFactoryBean validator = context.getBean(LocalValidatorFactoryBean.class);
            assertThatThrownBy(() -> validator.validate(new Request("anything", "valid")))
                    .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class);
        }
    }

    /**
     * 验证<code>allow.getBean(LocalValidatorFactoryBean.class).validate(request)</code>为空，<code>deny.getBean(LocalValidatorFactoryBean.class).validate(request)</code> 的大小为 <code>1</code>。
     */
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
     * 调用 <code>context.registerBean()</code>、<code>validator.setMessageInterpolator()</code>、<code>context.refresh()</code> 执行当前夹具操作。
     *
     * @param providers 本次调用使用的 <code>DataScopeProvider[]</code> 参数 <code>providers</code>
     * @return 根据测试条件返回的可能值，包括 <code>validator</code>、<code>context</code>
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

    /**
     * 将 <code>orderId</code>、<code>name</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    private static class Request {
        /**
         * 订单标识。
         */
        @RequiresDataPermissions(dataType = "order", message = "订单不可见")
        private final String orderId;
        /**
         * 当前测试模型用于展示和断言的名称。
         */
        @NotBlank
        private final String name;

        /**
         * 用给定值初始化 <code>Request</code> 实例。
         *
         * @param orderId 订单标识
         * @param name    <code>name</code> 字段
         */
        Request(String orderId, String name) {
            this.orderId = orderId;
            this.name = name;
        }
    }
}
