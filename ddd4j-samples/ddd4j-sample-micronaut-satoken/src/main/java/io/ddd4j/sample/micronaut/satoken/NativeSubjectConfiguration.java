package io.ddd4j.sample.micronaut.satoken;

import io.ddd4j.auth.satoken.subject.SaTokenSubjectProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class NativeSubjectConfiguration {
    @Singleton
    SubjectProvider subjects() {
        return new SaTokenSubjectProvider();
    }
}
