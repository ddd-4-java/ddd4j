package io.ddd4j.sample.dropwizard.satoken;

import io.ddd4j.web.dropwizard.Ddd4jDropwizardWebConfiguration;
import io.dropwizard.core.Configuration;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 原生认证入口配置，演示会话仅保存在内存。
 */
@Getter
@Setter
public class AuthConfiguration extends Configuration {
    /**
     * 当前示例使用的请求上下文和访问策略配置。
     */
    private Ddd4jDropwizardWebConfiguration ddd4jWeb = defaults();

    /**
     * 构造供认证示例启动的默认配置。
     *
     * @return 默认的认证和 Web 访问配置
     */
    private static Ddd4jDropwizardWebConfiguration defaults() {
        var web = new Ddd4jDropwizardWebConfiguration();
        web.setPublicPaths(List.of("/auth/login", "/health"));
        return web;
    }
}
