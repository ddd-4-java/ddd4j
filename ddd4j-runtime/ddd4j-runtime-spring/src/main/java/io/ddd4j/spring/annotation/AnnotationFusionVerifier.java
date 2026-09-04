package io.ddd4j.spring.annotation;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.lang.annotation.Annotation;
import java.util.Objects;

/**
 * 独立验证器：验证 ddd4j-runtime-spring 的 DDD 注解与 Spring 元注解融合正确。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public final class AnnotationFusionVerifier {

    private AnnotationFusionVerifier() {
    }

    public static void main(String[] args) {
        int passed = 0;
        int failed = 0;

        passed += verify("DomainService", DomainService.class, Service.class);
        passed += verify("DomainRepository", DomainRepository.class, Repository.class);
        passed += verify("ApplicationService", ApplicationService.class, Service.class);
        passed += verify("QueryService", QueryService.class, Service.class);
        passed += verify("CommandExecutor", CommandExecutor.class, Component.class);
        passed += verify("DomainEntity", DomainEntity.class, Component.class);
        passed += verify("DomainValueObject", DomainValueObject.class, Component.class);
        passed += verify("DomainGateway", DomainGateway.class, Component.class);
        passed += verify("DomainAssembler", DomainAssembler.class, Component.class);
        passed += verify("DomainConverter", DomainConverter.class, Component.class);

        log.info("");
        log.info("--- @DomainEvent 不下沉验证 ---");
        try {
            Class.forName("io.ddd4j.spring.annotation.DomainEvent");
            log.error("FAIL: @DomainEvent 不应在 ddd4j-runtime-spring 中");
            failed++;
        } catch (ClassNotFoundException ex) {
            log.info("PASS: @DomainEvent 不在 ddd4j-runtime-spring");
            passed++;
        }

        log.info("");
        log.info("========================================");
        log.info("总计: {} | 通过: {} | 失败: {}", passed + failed, passed, failed);
        log.info("========================================");

        if (failed > 0) {
            log.error("验证未通过");
            System.exit(1);
        }

        log.info("全部验证通过，ddd4j-runtime-spring 注解收敛完成");
    }

    private static int verify(String name,
                              Class<? extends Annotation> annotationType,
                              Class<? extends Annotation> springAnnotationType) {
        DDDAnnotation dddAnnotation = annotationType.getAnnotation(DDDAnnotation.class);
        if (Objects.isNull(dddAnnotation)) {
            log.error("FAIL {}: 缺少 @DDDAnnotation 元注解", name);
            return 0;
        }
        log.info("PASS {}: 已标注 @DDDAnnotation", name);

        Annotation springAnnotation = annotationType.getAnnotation(springAnnotationType);
        if (Objects.isNull(springAnnotation)) {
            log.error("FAIL {}: 缺少 {} 元注解", name, springAnnotationType.getSimpleName());
            return 0;
        }
        log.info("PASS {}: 已融合 @{}", name, springAnnotationType.getSimpleName());
        return 1;
    }
}
