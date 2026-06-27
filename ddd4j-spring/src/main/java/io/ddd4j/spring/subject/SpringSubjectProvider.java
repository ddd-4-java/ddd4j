package io.ddd4j.spring.subject;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Spring 实现的 Subject 提供者
 * <p>
 * 通过 Spring ApplicationContext 注入 Subject 实现。
 *
 * @author Loong Wan
 */
@Slf4j
@Component
public class SpringSubjectProvider implements SubjectProvider {

    private final Subject subject;

    @Autowired
    public SpringSubjectProvider(Subject subject) {
        this.subject = subject;
    }

    @Override
    public Subject getSubject() {
        if (subject == null) {
            log.debug("No Subject implementation found in Spring container");
            return null;
        }
        return subject;
    }
}
