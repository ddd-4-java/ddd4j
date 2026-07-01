package io.ddd4j.quarkus.core.extension;

import io.ddd4j.quarkus.annotation.ddd.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * CDI 扩展：扫描 DDD 构造型注解并自动注册为 CDI Bean。
 */
@Slf4j
public class DddCdiExtension implements Extension {

    <T> void processAnnotatedType(@Observes ProcessAnnotatedType<T> event) {
        AnnotatedType<T> type = event.getAnnotatedType();
        String annotationName = resolveAnnotationName(type);
        if (Objects.isNull(annotationName)) {
            return;
        }
        if (!type.isAnnotationPresent(ApplicationScoped.class)) {
            log.debug("Adding @ApplicationScoped to {} {}", annotationName, type.getJavaClass().getName());
            event.configureAnnotatedType().add(new ApplicationScopedLiteral());
        }
    }

    private <T> String resolveAnnotationName(AnnotatedType<T> type) {
        if (type.isAnnotationPresent(ApplicationService.class)) {
            return "@ApplicationService";
        }
        if (type.isAnnotationPresent(DomainService.class)) {
            return "@DomainService";
        }
        if (type.isAnnotationPresent(DomainRepository.class)) {
            return "@DomainRepository";
        }
        if (type.isAnnotationPresent(DomainAssembler.class)) {
            return "@DomainAssembler";
        }
        if (type.isAnnotationPresent(DomainConverter.class)) {
            return "@DomainConverter";
        }
        if (type.isAnnotationPresent(DomainEntity.class)) {
            return "@DomainEntity";
        }
        if (type.isAnnotationPresent(DomainValueObject.class)) {
            return "@DomainValueObject";
        }
        if (type.isAnnotationPresent(DomainGateway.class)) {
            return "@DomainGateway";
        }
        if (type.isAnnotationPresent(QueryService.class)) {
            return "@QueryService";
        }
        if (type.isAnnotationPresent(CommandExecutor.class)) {
            return "@CommandExecutor";
        }
        return null;
    }

    private static class ApplicationScopedLiteral extends jakarta.enterprise.util.AnnotationLiteral<ApplicationScoped>
            implements ApplicationScoped {

        private static final long serialVersionUID = 1L;
    }
}
