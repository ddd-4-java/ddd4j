package io.ddd4j.data.datascope;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 <code>DataScopeProvider</code> 在实际装配或测试替身中的行为。 */
class DataScopeProviderTest {

    /** 验证<code>provider.hasPermissions("dept", "001")</code>为真，<code>provider.hasPermissions("dept", null)</code>为假。 */
    @Test
    void defaultProviderShouldAllowOnlyNonNullData() {
        DataScopeProvider provider = DataScopeProvider.nonNullAllowed();

        assertThat(provider.hasPermissions("dept", "001")).isTrue();
        assertThat(provider.hasPermissions("dept", null)).isFalse();
    }

    /** 验证<code>provider.hasPermissions("tenant", "t1")</code>为真，<code>provider.hasPermissions("tenant", "t2")</code>为假。 */
    @Test
    void customProviderShouldOverrideDefaultProvider() {
        DataScopeProvider provider = (type, data) -> "tenant".equals(type) && "t1".equals(data);

        assertThat(provider.hasPermissions("tenant", "t1")).isTrue();
        assertThat(provider.hasPermissions("tenant", "t2")).isFalse();
    }

    /**
     * 验证<code>validator.isValid("p1", null)</code>为真，<code>validator.isValid("p2", null)</code>为假，<code>validator.isValid(null, null)</code>为假。
     *
     * @throws NoSuchFieldException 反射定位被测字段失败
     */
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

    /** 集中保存 <code>project</code>，供当前模块使用。 */
    private static class Sample {

        /** 保存 <code>"project") private String project</code>，供 <code>Sample</code> 的处理流程复用。 */
        @io.ddd4j.data.datascope.annotation.RequiresDataPermissions(dataType = "project")
        private String project;
    }
}
