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
    /** \u5f53\u524d\u793a\u4f8b\u4f7f\u7528\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587\u548c\u8bbf\u95ee\u7b56\u7565\u914d\u7f6e\u3002 */
    private Ddd4jDropwizardWebConfiguration ddd4jWeb = defaults();

    /**
     * \u6784\u9020\u4f9b\u8ba4\u8bc1\u793a\u4f8b\u542f\u52a8\u7684\u9ed8\u8ba4\u914d\u7f6e\u3002
     * @return \u9ed8\u8ba4\u7684\u8ba4\u8bc1\u548c Web \u8bbf\u95ee\u914d\u7f6e
     */
    private static Ddd4jDropwizardWebConfiguration defaults() {
        var web = new Ddd4jDropwizardWebConfiguration();
        web.setPublicPaths(List.of("/auth/login", "/health"));
        return web;
    }
}
