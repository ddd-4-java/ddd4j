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
    /** \u5b89\u88c5\u6846\u67b6\u4e3b\u4f53\u63d0\u4f9b\u5668\u524d\u5df2\u5b58\u5728\u7684\u5168\u5c40\u63d0\u4f9b\u5668\u3002 */
    private SubjectProvider previousSubjectProvider;
    /** \u5b89\u88c5\u6846\u67b6\u56fd\u9645\u5316\u7ec4\u4ef6\u524d\u5df2\u5b58\u5728\u7684\u6d88\u606f\u63d0\u4f9b\u5668\u3002 */
    private I18nProvider previousI18nProvider;

    /**
     * \u628a CDI \u5e94\u7528\u7684\u4e3b\u4f53\u53ca\u56fd\u9645\u5316\u7ec4\u4ef6\u5b89\u88c5\u5230\u5171\u4eab\u5165\u53e3\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
    void onStart(@Observes StartupEvent event) {
        previousI18nProvider = I18nKit.getProvider();
        I18nKit.register(i18nProvider);
        log.info("Registered I18nProvider for Quarkus CDI");

        previousSubjectProvider = SubjectKit.subjectProvider;
        SubjectKit.register(subjectProvider);
        log.info("Registered SubjectProvider for Quarkus CDI");
    }

    /**
     * \u5728 Quarkus \u505c\u6b62\u65f6\u64a4\u9500\u5f53\u524d\u521d\u59cb\u5316\u5668\u62e5\u6709\u7684\u6ce8\u518c\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
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
