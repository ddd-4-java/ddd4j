package io.ddd4j.quarkus.annotation.ddd;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * 独立验证器：验证 ddd4j-runtime-quarkus 的 DDD 注解与 Jakarta CDI 元注解融合正确。
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

        passed += verify("DomainService", DomainService.class);
        passed += verify("DomainRepository", DomainRepository.class);
        passed += verify("ApplicationService", ApplicationService.class);
        passed += verify("QueryService", QueryService.class);
        passed += verify("CommandExecutor", CommandExecutor.class);
        passed += verify("DomainEntity", DomainEntity.class);
        passed += verify("DomainValueObject", DomainValueObject.class);
        passed += verify("DomainGateway", DomainGateway.class);
        passed += verify("DomainAssembler", DomainAssembler.class);
        passed += verify("DomainConverter", DomainConverter.class);

        log.info("");
        log.info("--- @DomainEvent 不下沉验证 ---");
        try {
            Class.forName("io.ddd4j.quarkus.annotation.ddd.DomainEvent");
            log.error("FAIL: @DomainEvent 不应在 ddd4j-runtime-quarkus 中");
            failed++;
        } catch (ClassNotFoundException ex) {
            log.info("PASS: @DomainEvent 不在 ddd4j-runtime-quarkus");
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

        log.info("全部验证通过，ddd4j-runtime-quarkus 注解收敛完成");
    }

    private static int verify(String name, Class<? extends java.lang.annotation.Annotation> annotationType) {
        DDDAnnotation dddAnnotation = annotationType.getAnnotation(DDDAnnotation.class);
        if (Objects.isNull(dddAnnotation)) {
            log.error("FAIL {}: 缺少 @DDDAnnotation 元注解", name);
            return 0;
        }
        log.info("PASS {}: 已标注 @DDDAnnotation", name);

        ApplicationScoped applicationScoped = annotationType.getAnnotation(ApplicationScoped.class);
        if (Objects.isNull(applicationScoped)) {
            log.error("FAIL {}: 缺少 @ApplicationScoped 元注解", name);
            return 0;
        }
        log.info("PASS {}: 已融合 @ApplicationScoped", name);
        return 1;
    }
}
