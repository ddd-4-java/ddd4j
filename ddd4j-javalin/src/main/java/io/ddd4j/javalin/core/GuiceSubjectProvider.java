package io.ddd4j.javalin.core;

import com.google.inject.Inject;
import com.google.inject.Injector;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Javalin/Guice 实现的 Subject 提供者
 * <p>
 * 通过 Guice {@link Injector} 查找已绑定的 Subject 实现。
 *
 * @author wandl
 * @since 3.4.x
 */
@Slf4j
public class GuiceSubjectProvider implements SubjectProvider {

    @Inject
    private Injector injector;

    @Override
    public Subject getSubject() {
        Optional<Subject> subject = Optional.ofNullable(injector)
                .map(inj -> {
                    try {
                        return inj.getInstance(Subject.class);
                    } catch (Exception e) {
                        log.debug("No Subject binding found: {}", e.getMessage());
                        return null;
                    }
                });
        return subject.orElse(null);
    }
}
