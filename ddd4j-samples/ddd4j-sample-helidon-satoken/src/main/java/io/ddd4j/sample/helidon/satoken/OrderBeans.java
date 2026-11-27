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
     * \u66b4\u9732\u5f53\u524d\u5e94\u7528\u539f\u751f\u8ba4\u8bc1\u7684\u4e3b\u4f53\u63d0\u4f9b\u5668\u3002
     * @param auth \u5f53\u524d\u5e94\u7528\u63d0\u4f9b\u7684\u539f\u751f\u8ba4\u8bc1\u7ec4\u4ef6
     * @return \u7528\u4e8e\u89e3\u6790\u8ba4\u8bc1\u4e3b\u4f53\u7684\u6846\u67b6\u9002\u914d\u5668
     */
    @Produces
    @Singleton
    SubjectProvider subjects(NativeAuth auth) {
        return new SaTokenSubjectProvider();
    }
}
