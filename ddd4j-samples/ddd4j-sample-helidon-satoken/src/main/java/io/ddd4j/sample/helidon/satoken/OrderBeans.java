package io.ddd4j.sample.helidon.satoken;

import io.ddd4j.auth.satoken.subject.SaTokenSubjectProvider;
import io.ddd4j.core.subject.SubjectProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * 当前样例只装配本应用原生认证。
 */
@ApplicationScoped
public class OrderBeans {
    /**
     * 暴露当前应用原生认证的主体提供器。
     *
     * @param auth 当前应用提供的原生认证组件
     * @return 用于解析认证主体的框架适配器
     */
    @Produces
    @Singleton
    SubjectProvider subjects(NativeAuth auth) {
        return new SaTokenSubjectProvider();
    }
}
