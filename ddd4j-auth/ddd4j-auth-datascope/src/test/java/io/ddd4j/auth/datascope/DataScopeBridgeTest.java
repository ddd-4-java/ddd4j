package io.ddd4j.auth.datascope;

import io.ddd4j.auth.datascope.annotation.RequiresDataPermissions;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.extension.utils.SpringContextUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 旧 Spring 数据权限入口和框架中立策略的兼容契约。 */
class DataScopeBridgeTest {
    @Test
    void anonymousLegacyProviderIsNeutralAndPreservesDefaults() {
        DataScopeProvider provider = new DataScopeProvider() {};
        assertThat(provider).isInstanceOf(io.ddd4j.data.datascope.DataScopeProvider.class);
        assertThat(provider.hasPermissions(null, "")).isTrue();
        assertThat(provider.hasPermissions("order", null)).isFalse();
    }

    @Test
    void springLegacyNameAndCustomPolicyRemainAuthoritative() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        DataScopeProvider custom = new DataScopeProvider() {
            @Override
            public boolean hasPermissions(String type, Object data) {
                calls.incrementAndGet();
                return "order".equals(type) && "allowed".equals(data);
            }
        };
        ApplicationContext previous = SpringContextUtils.getContext().getApplicationContext();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("legacyOrderPermission", DataScopeProvider.class, () -> custom);
            context.refresh();
            SpringContextUtils.getContext().setApplicationContext(context);
            assertThat(context.getBean("legacyOrderPermission")).isSameAs(custom);
            assertThat(context.getBean(io.ddd4j.data.datascope.DataScopeProvider.class)).isSameAs(custom);
            RequiresDataPermissionsValidator validator = new RequiresDataPermissionsValidator();
            validator.initialize(Request.class.getDeclaredField("data").getAnnotation(RequiresDataPermissions.class));
            assertThat(validator.isValid("allowed", null)).isTrue();
            assertThat(validator.isValid("denied", null)).isFalse();
            assertThat(validator.isValid(null, null)).isFalse();
            assertThat(calls).hasValue(2);
        } finally {
            SpringContextUtils.getContext().setApplicationContext(previous);
        }
    }

    @Test
    void missingLegacyProviderStillRejectsInitializationAndUninitializedValues() throws Exception {
        ApplicationContext previous = SpringContextUtils.getContext().getApplicationContext();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            SpringContextUtils.getContext().setApplicationContext(context);
            RequiresDataPermissionsValidator validator = new RequiresDataPermissionsValidator();
            assertThat(validator.isValid("allowed", null)).isFalse();
            assertThat(validator.isValid(null, null)).isFalse();
            assertThatThrownBy(() -> validator.initialize(Request.class.getDeclaredField("data")
                    .getAnnotation(RequiresDataPermissions.class)))
                    .isInstanceOf(org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
        } finally {
            SpringContextUtils.getContext().setApplicationContext(previous);
        }
    }

    private static final class Request {
        @RequiresDataPermissions(dataType = "order")
        private String data;
    }
}
