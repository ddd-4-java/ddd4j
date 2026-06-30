package io.ddd4j.data.datascope;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DataScopeAutoConfiguration.class);

    @Test
    void defaultProviderShouldAllowOnlyNonNullData() {
        contextRunner.run(context -> {
            DataScopeProvider provider = context.getBean(DataScopeProvider.class);

            assertThat(provider.hasPermissions("dept", "001")).isTrue();
            assertThat(provider.hasPermissions("dept", null)).isFalse();
        });
    }

    @Test
    void customProviderShouldOverrideDefaultProvider() {
        new ApplicationContextRunner()
                .withBean(DataScopeProvider.class, () -> (type, data) -> "tenant".equals(type) && "t1".equals(data))
                .withUserConfiguration(DataScopeAutoConfiguration.class)
                .run(context -> {
                    DataScopeProvider provider = context.getBean(DataScopeProvider.class);

                    assertThat(provider.hasPermissions("tenant", "t1")).isTrue();
                    assertThat(provider.hasPermissions("tenant", "t2")).isFalse();
                });
    }

    @Test
    void validatorShouldDelegateToProvider() throws NoSuchFieldException {
        DataScopeProvider provider = (type, data) -> "project".equals(type) && "p1".equals(data);
        RequiresDataPermissionsValidator validator = new RequiresDataPermissionsValidator(provider);
        validator.initialize(Sample.class.getDeclaredField("project").getAnnotation(
                io.ddd4j.data.datascope.annotation.RequiresDataPermissions.class));

        assertThat(validator.isValid("p1", null)).isTrue();
        assertThat(validator.isValid("p2", null)).isFalse();
        assertThat(validator.isValid(null, null)).isFalse();
    }

    private static class Sample {

        @io.ddd4j.data.datascope.annotation.RequiresDataPermissions(dataType = "project")
        private String project;
    }
}
