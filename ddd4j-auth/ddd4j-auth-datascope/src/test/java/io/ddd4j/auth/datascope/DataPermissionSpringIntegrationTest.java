package io.ddd4j.auth.datascope;

import io.github.easy4j.validation.constraints.RequiresDataPermissions;
import io.github.easy4j.validation.provider.DataScopeProvider;
import javax.validation.constraints.NotBlank;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** auth 直接复用 Spring 原生工厂与 Easy4j 权限契约，不注册默认放行策略。 */
class DataPermissionSpringIntegrationTest {
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

    @Test
    void missingProviderFailsInsteadOfGrantingAccess() {
        try (AnnotationConfigApplicationContext context = context()) {
            assertThat(context.getBeansOfType(DataScopeProvider.class)).isEmpty();
            LocalValidatorFactoryBean validator = context.getBean(LocalValidatorFactoryBean.class);
            assertThatThrownBy(() -> validator.validate(new Request("anything", "valid")))
                    .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class);
        }
    }

    @Test
    void ambiguousProvidersFailInsteadOfSelectingAnArbitraryPolicy() {
        try (AnnotationConfigApplicationContext context = context((type, value) -> true, (type, value) -> false)) {
            LocalValidatorFactoryBean validator = context.getBean(LocalValidatorFactoryBean.class);
            assertThatThrownBy(() -> validator.validate(new Request("anything", "valid")))
                    .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class);
        }
    }

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

    private static class Request {
        @RequiresDataPermissions(dataType = "order", message = "订单不可见")
        private final String orderId;
        @NotBlank
        private final String name;

        Request(String orderId, String name) {
            this.orderId = orderId;
            this.name = name;
        }
    }
}
