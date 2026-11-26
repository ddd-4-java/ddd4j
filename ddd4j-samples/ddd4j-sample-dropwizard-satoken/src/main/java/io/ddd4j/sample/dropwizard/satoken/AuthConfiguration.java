package io.ddd4j.sample.dropwizard.satoken;

import com.google.common.collect.ImmutableList;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardWebConfiguration;
import io.dropwizard.Configuration;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 原生认证入口配置，演示会话仅保存在内存。
 */
@Getter
@Setter
public class AuthConfiguration extends Configuration {
    private Ddd4jDropwizardWebConfiguration ddd4jWeb = defaults();

    private static Ddd4jDropwizardWebConfiguration defaults() {
        io.ddd4j.web.dropwizard.Ddd4jDropwizardWebConfiguration web = new Ddd4jDropwizardWebConfiguration();
        web.setPublicPaths(ImmutableList.of("/auth/login", "/health"));
        return web;
    }
}
