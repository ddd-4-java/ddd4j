package io.ddd4j.auth.spring;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class SubjectRegistrarTest {

    @Test
    void shouldRegisterSubjectProviderAfterInitialization() {
        SubjectProvider provider = new SubjectProvider() {
            /**
             * 获取值。
             * @return 方法执行结果。
             */
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
