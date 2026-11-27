package io.ddd4j.auth.spring;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 核对 Spring 认证主体登记器的优先级和 Bean 后处理行为。
 */
class SubjectRegistrarTest {

    /**
     * 验证<code>result</code> 指向同一实例 <code>provider</code>，<code>SubjectKit.subjectProvider</code> 指向同一实例 <code>provider</code>。
     */
    @Test
    void shouldRegisterSubjectProviderAfterInitialization() {
        SubjectProvider provider = new SubjectProvider() {
            @Override
            public Subject getSubject() {
                return null;
            }
        };

        Object result = new SubjectRegistrar().postProcessAfterInitialization(provider, "subjectProvider");

        assertThat(result).isSameAs(provider);
        assertThat(SubjectKit.subjectProvider).isSameAs(provider);
    }
}
