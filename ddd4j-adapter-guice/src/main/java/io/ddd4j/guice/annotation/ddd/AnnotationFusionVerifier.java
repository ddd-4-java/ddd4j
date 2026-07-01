package io.ddd4j.guice.annotation.ddd;

import com.google.inject.Singleton;
import io.ddd4j.annotation.ddd.DDDAnnotation;
import io.ddd4j.guice.annotation.web.*;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

/**
 * 独立验证器：验证 ddd4j-adapter-guice 内聚的 11 个 DDD 注解
 * 与 Guice @Singleton 元注解融合 + 7 个 web/ 路由参数注解。
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

        log.info("=== DDD 注解元注解融合验证 ===");
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
        log.info("=== @DomainEvent 不下沉验证 ===");
        try {
            Class.forName("io.ddd4j.guice.annotation.ddd.DomainEvent");
            log.error("FAIL: @DomainEvent 不应在 ddd4j-adapter-guice 聚合注解中");
            failed++;
        } catch (ClassNotFoundException e) {
            log.info("PASS: @DomainEvent 不在 ddd4j-adapter-guice 聚合注解中");
            passed++;
        }

        log.info("");
        log.info("=== Web 路由参数注解验证 ===");
        passed += verifyWebAnnotation("PathParam", PathParam.class);
        passed += verifyWebAnnotation("QueryParam", QueryParam.class);
        passed += verifyWebAnnotation("FormParam", FormParam.class);
        passed += verifyWebAnnotation("HeaderParam", HeaderParam.class);
        passed += verifyWebAnnotation("CookieParam", CookieParam.class);
        passed += verifyWebAnnotation("BodyParam", BodyParam.class);
        passed += verifyWebAnnotation("Context", Context.class);

        log.info("");
        log.info("=== 业务代码使用模式验证 ===");
        java.lang.annotation.Annotation domainServiceOnBiz = BusinessDomainService.class.getAnnotation(DomainService.class);
        if (Objects.isNull(domainServiceOnBiz)) {
            log.error("FAIL: 业务类未标注 @DomainService");
            failed++;
        } else {
            DDDAnnotation ddd = DomainService.class.getAnnotation(DDDAnnotation.class);
            Singleton singleton = DomainService.class.getAnnotation(Singleton.class);
            if (Objects.nonNull(ddd) && Objects.nonNull(singleton)) {
                log.info("PASS: 业务代码 @DomainService -> 元注解链路完整（@DDDAnnotation + @Singleton 可被框架识别）");
                passed++;
            } else {
                log.error("FAIL: @DomainService 元注解缺失");
                failed++;
            }
        }

        log.info("");
        log.info("========================================");
        int total = passed + failed;
        log.info("总计: {} | 通过: {} | 失败: {}", total, passed, failed);
        log.info("========================================");

        if (failed > 0) {
            log.error("VERIFICATION FAILED");
            System.exit(1);
        } else {
            log.info("ALL PASSED! ddd4j-adapter-guice 注解收敛符合架构设计");
        }
    }

    private static int verify(
            String name,
            Class<? extends java.lang.annotation.Annotation> dddAnnotation) {

        DDDAnnotation ddd = dddAnnotation.getAnnotation(DDDAnnotation.class);
        if (Objects.isNull(ddd)) {
            log.error("FAIL {}: 缺少 @DDDAnnotation", name);
            return 0;
        }
        Singleton singleton = dddAnnotation.getAnnotation(Singleton.class);
        if (Objects.isNull(singleton)) {
            log.error("FAIL {}: 缺少 @Singleton", name);
            return 0;
        }
        log.info("PASS {}: @DDDAnnotation + @Singleton", name);
        return 1;
    }

    private static int verifyWebAnnotation(
            String name,
            Class<? extends java.lang.annotation.Annotation> annotation) {
        Target target = annotation.getAnnotation(Target.class);
        if (Objects.isNull(target) || target.value().length == 0
                || target.value()[0] != ElementType.PARAMETER) {
            log.error("FAIL {}: @Target 必须为 PARAMETER", name);
            return 0;
        }
        Retention retention = annotation.getAnnotation(Retention.class);
        if (Objects.isNull(retention) || retention.value() != RetentionPolicy.RUNTIME) {
            log.error("FAIL {}: @Retention 必须为 RUNTIME", name);
            return 0;
        }
        log.info("PASS {}: @Target PARAMETER + @Retention RUNTIME", name);
        return 1;
    }
}

@DomainService
class BusinessDomainService {
    public String hello() {
        return "hello";
    }
}
