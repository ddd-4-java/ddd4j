package io.ddd4j.sample.micronaut.shiro;

import io.ddd4j.auth.shiro.subject.ShiroSubjectProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/** \u5411 Micronaut \u5bb9\u5668\u6ce8\u518c\u5f53\u524d\u793a\u4f8b\u7684\u539f\u751f\u8ba4\u8bc1\u4e3b\u4f53\u63d0\u4f9b\u5668\u3002 */
@Factory
public class NativeSubjectConfiguration {
    /**
     * \u5411\u5bb9\u5668\u66b4\u9732\u7528\u4e8e\u89e3\u6790\u5f53\u524d\u8ba4\u8bc1\u4e3b\u4f53\u7684\u539f\u751f\u6846\u67b6\u63d0\u4f9b\u5668\u3002
     * @return \u5f53\u524d\u793a\u4f8b\u7684\u539f\u751f\u4e3b\u4f53\u63d0\u4f9b\u5668
     */
    @Singleton
    SubjectProvider subjects() {
        return new ShiroSubjectProvider();
    }
}
