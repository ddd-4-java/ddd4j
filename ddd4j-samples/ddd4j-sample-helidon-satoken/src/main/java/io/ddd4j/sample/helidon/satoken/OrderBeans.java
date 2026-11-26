package io.ddd4j.sample.helidon.satoken;

import io.ddd4j.auth.satoken.subject.SaTokenSubjectProvider;
import io.ddd4j.core.subject.SubjectProvider;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

/**
 * 当前样例只装配本应用原生认证。
 */
@ApplicationScoped
public class OrderBeans {
    @Produces
    @Singleton
    SubjectProvider subjects(NativeAuth auth) {
        return new SaTokenSubjectProvider();
    }
}
