package io.ddd4j.spring.subject;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Spring 实现的 Subject 提供者
 * <p>
 * 通过 Spring ApplicationContext 注入 Subject 实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@Component
public class SpringSubjectProvider implements SubjectProvider {

    /** Subject 实例 */
    private final Subject subject;

    @Autowired
    public SpringSubjectProvider(Subject subject) {
        this.subject = subject;
    }

    @Override
    public Subject getSubject() {
        if (Objects.isNull(subject)) {
            log.debug("No Subject implementation found in Spring container");
            return null;
        }
        return subject;
    }
}
