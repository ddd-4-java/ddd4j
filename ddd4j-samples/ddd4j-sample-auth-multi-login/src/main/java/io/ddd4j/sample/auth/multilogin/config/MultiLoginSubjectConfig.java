package io.ddd4j.sample.auth.multilogin.config;

import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.auth.multilogin.subject.InMemorySubject;
import io.ddd4j.sample.auth.multilogin.subject.InMemorySubjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MultiLoginSubjectConfig {

    @Bean
    public InMemorySubject inMemorySubject(ApplicationEventPublisher eventPublisher) {
        return new InMemorySubject(eventPublisher);
    }

    @Bean
    public SubjectProvider subjectProvider(InMemorySubject subject) {
        SubjectProvider provider = new InMemorySubjectProvider(subject);
        SubjectKit.register(provider);
        return provider;
    }
}
