package io.ddd4j.auth.spring;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** \u6838\u5bf9 Spring \u8ba4\u8bc1\u4e3b\u4f53\u767b\u8bb0\u5668\u7684\u4f18\u5148\u7ea7\u548c Bean \u540e\u5904\u7406\u884c\u4e3a\u3002 */
class SubjectRegistrarTest {

    /** \u9a8c\u8bc1<code>result</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>provider</code>\uff0c<code>SubjectKit.subjectProvider</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>provider</code>\u3002 */
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
