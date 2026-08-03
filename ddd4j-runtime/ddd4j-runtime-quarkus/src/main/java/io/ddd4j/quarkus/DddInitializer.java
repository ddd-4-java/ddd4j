package io.ddd4j.quarkus;

import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.I18nKit;
import io.ddd4j.core.util.SubjectKit;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Quarkus 启动初始化器：在应用启动时注册 I18nProvider 和 SubjectProvider。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
@ApplicationScoped
public class DddInitializer {

    /**
     * 国际化提供者
     */
    @Inject
    I18nProvider i18nProvider;

    /**
     * Subject 提供者
     */
    @Inject
    SubjectProvider subjectProvider;
    private SubjectProvider previousSubjectProvider;
    private I18nProvider previousI18nProvider;

    void onStart(@Observes StartupEvent event) {
        previousI18nProvider = I18nKit.getProvider();
        I18nKit.register(i18nProvider);
        log.info("Registered I18nProvider for Quarkus CDI");

        previousSubjectProvider = SubjectKit.subjectProvider;
        SubjectKit.register(subjectProvider);
        log.info("Registered SubjectProvider for Quarkus CDI");
    }

    void onStop(@Observes ShutdownEvent event) {
        if (Objects.equals(SubjectKit.subjectProvider, subjectProvider)) {
            SubjectKit.register(previousSubjectProvider);
        }
        if (Objects.equals(I18nKit.getProvider(), i18nProvider)) {
            I18nKit.register(previousI18nProvider);
        }
        previousSubjectProvider = null;
        previousI18nProvider = null;
    }
}
