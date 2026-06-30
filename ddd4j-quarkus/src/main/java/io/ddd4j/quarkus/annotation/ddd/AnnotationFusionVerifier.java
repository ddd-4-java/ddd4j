package io.ddd4j.quarkus.annotation.ddd;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * 独立验证器：验证 ddd4j-quarkus 的 DDD 注解与 Jakarta CDI 元注解融合正确。
 */
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

        System.out.println();
        System.out.println("--- @DomainEvent 不下沉验证 ---");
        try {
            Class.forName("io.ddd4j.quarkus.annotation.ddd.DomainEvent");
            System.out.println("FAIL: @DomainEvent 不应在 ddd4j-quarkus 中");
            failed++;
        } catch (ClassNotFoundException ex) {
            System.out.println("PASS: @DomainEvent 不在 ddd4j-quarkus");
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

        System.out.println("全部验证通过，ddd4j-quarkus 注解收敛完成");
    }

    private static int verify(String name, Class<? extends java.lang.annotation.Annotation> annotationType) {
        DDDAnnotation dddAnnotation = annotationType.getAnnotation(DDDAnnotation.class);
        if (java.util.Objects.isNull(dddAnnotation)) {
            System.out.println("FAIL " + name + ": 缺少 @DDDAnnotation 元注解");
            return 0;
        }
        System.out.println("PASS " + name + ": 已标注 @DDDAnnotation");

        ApplicationScoped applicationScoped = annotationType.getAnnotation(ApplicationScoped.class);
        if (java.util.Objects.isNull(applicationScoped)) {
            System.out.println("FAIL " + name + ": 缺少 @ApplicationScoped 元注解");
            return 0;
        }
        System.out.println("PASS " + name + ": 已融合 @ApplicationScoped");
        return 1;
    }
}
