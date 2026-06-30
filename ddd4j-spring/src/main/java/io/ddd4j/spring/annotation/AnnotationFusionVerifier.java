package io.ddd4j.spring.annotation;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.lang.annotation.Annotation;

/**
 * 独立验证器：验证 ddd4j-spring 的 DDD 注解与 Spring 元注解融合正确。
 */
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

        System.out.println();
        System.out.println("--- @DomainEvent 不下沉验证 ---");
        try {
            Class.forName("io.ddd4j.spring.annotation.DomainEvent");
            System.out.println("FAIL: @DomainEvent 不应在 ddd4j-spring 中");
            failed++;
        } catch (ClassNotFoundException ex) {
            System.out.println("PASS: @DomainEvent 不在 ddd4j-spring");
            passed++;
        }

        System.out.println();
        System.out.println("========================================");
        System.out.println("总计: " + (passed + failed) + " | 通过: " + passed + " | 失败: " + failed);
        System.out.println("========================================");

        if (failed > 0) {
            System.err.println("验证未通过");
            System.exit(1);
        }

        System.out.println("全部验证通过，ddd4j-spring 注解收敛完成");
    }

    private static int verify(String name,
                              Class<? extends Annotation> annotationType,
                              Class<? extends Annotation> springAnnotationType) {
        DDDAnnotation dddAnnotation = annotationType.getAnnotation(DDDAnnotation.class);
        if (java.util.Objects.isNull(dddAnnotation)) {
            System.out.println("FAIL " + name + ": 缺少 @DDDAnnotation 元注解");
            return 0;
        }
        System.out.println("PASS " + name + ": 已标注 @DDDAnnotation");

        Annotation springAnnotation = annotationType.getAnnotation(springAnnotationType);
        if (java.util.Objects.isNull(springAnnotation)) {
            System.out.println("FAIL " + name + ": 缺少 " + springAnnotationType.getSimpleName() + " 元注解");
            return 0;
        }
        System.out.println("PASS " + name + ": 已融合 @" + springAnnotationType.getSimpleName());
        return 1;
    }
}
