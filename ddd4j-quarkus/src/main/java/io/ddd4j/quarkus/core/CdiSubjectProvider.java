package io.ddd4j.quarkus.core;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Quarkus CDI 实现的 Subject 提供者
 * <p>
 * 通过 CDI {@code Instance<Subject>} 查找已注册的 Subject Bean。
 *
 * @author Loong Wan
 * @since 3.4.x
 */
@Slf4j
@ApplicationScoped
public class CdiSubjectProvider implements SubjectProvider {

    @Inject
    Instance<Subject> subjectInstance;

    @Override
    public Subject getSubject() {
        if (subjectInstance.isUnsatisfied() || subjectInstance.isAmbiguous()) {
            log.debug("No unique Subject bean found, returning null");
            return null;
        }
        return subjectInstance.get();
    }
}
