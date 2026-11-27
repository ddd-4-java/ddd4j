package io.ddd4j.sample.micronaut.satoken;

import io.ddd4j.auth.satoken.subject.SaTokenSubjectProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/**
 * 向 Micronaut 容器注册当前示例的原生认证主体提供器。
 */
@Factory
public class NativeSubjectConfiguration {
    /**
     * 向容器暴露用于解析当前认证主体的原生框架提供器。
     *
     * @return 当前示例的原生主体提供器
     */
    @Singleton
    SubjectProvider subjects() {
        return new SaTokenSubjectProvider();
    }
}
