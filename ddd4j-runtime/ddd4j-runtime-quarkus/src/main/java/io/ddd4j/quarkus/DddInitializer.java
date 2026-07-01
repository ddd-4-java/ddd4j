package io.ddd4j.quarkus;

import io.ddd4j.core.context.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.I18nKit;
import io.ddd4j.core.util.SubjectKit;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Quarkus 启动初始化器：在应用启动时注册 I18nProvider 和 SubjectProvider。
 */
@Slf4j
@ApplicationScoped
public class DddInitializer {

    @Inject
    I18nProvider i18nProvider;

    @Inject
    SubjectProvider subjectProvider;

    void onStart(@Observes StartupEvent event) {
        I18nKit.register(i18nProvider);
        log.info("Registered I18nProvider for Quarkus CDI");

        SubjectKit.register(subjectProvider);
        log.info("Registered SubjectProvider for Quarkus CDI");
    }
}
