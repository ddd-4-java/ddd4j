package io.ddd4j.data.datascope;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for data-scope validation support.
 */
@AutoConfiguration
public class DataScopeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DataScopeProvider dataScopeProvider() {
        return DataScopeProvider.nonNullAllowed();
    }

    @Bean
    @ConditionalOnMissingBean
    public RequiresDataPermissionsValidator requiresDataPermissionsValidator(DataScopeProvider provider) {
        return new RequiresDataPermissionsValidator(provider);
    }
}
