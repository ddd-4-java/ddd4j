package io.ddd4j.sample.micronaut.shiro;

import io.ddd4j.auth.shiro.subject.ShiroSubjectProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class NativeSubjectConfiguration {
    @Singleton
    SubjectProvider subjects() {
        return new ShiroSubjectProvider();
    }
}
