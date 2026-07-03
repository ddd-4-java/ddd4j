package io.ddd4j.quarkus.subject;

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
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
@ApplicationScoped
public class CdiSubjectProvider implements SubjectProvider {

    /** CDI Subject 实例 */
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
