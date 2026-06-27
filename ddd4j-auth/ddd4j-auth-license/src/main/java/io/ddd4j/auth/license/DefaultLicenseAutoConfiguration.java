package io.ddd4j.auth.license;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DefaultLicenseAutoConfiguration {
    @Bean(initMethod = "installLicense", destroyMethod = "unInstallLicense")
    public LicenseVerify licenseVerify(LicenseProperties properties) {
        return new LicenseVerify(properties.getSubject(), properties.getPublicAlias(), properties.getStorePass(),
                properties.getLicensePath(), properties.getPublicKeysStorePath());
    }

}
