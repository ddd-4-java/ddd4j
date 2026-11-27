package io.ddd4j.guice.annotation.ddd;

import com.google.inject.Singleton;
import io.ddd4j.annotation.ddd.DDDAnnotation;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * 独立验证器：验证 ddd4j-runtime-guice 内聚的 DDD 注解与 Guice @Singleton 元注解融合。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public final class AnnotationFusionVerifier {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>AnnotationFusionVerifier</code> \u5b9e\u4f8b\u3002 */
    private AnnotationFusionVerifier() {
    }

    /**
     * \u9a8c\u8bc1 Guice DDD \u6ce8\u89e3\u7684\u5143\u6ce8\u89e3\u7ec4\u5408\u3002
     * @param args \u547d\u4ee4\u884c\u53c2\u6570\uff0c\u5f53\u524d\u672a\u4f7f\u7528
     */
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
            log.error("FAIL: @DomainEvent 不应在 ddd4j-runtime-guice 聚合注解中");
            failed++;
        } catch (ClassNotFoundException e) {
            log.info("PASS: @DomainEvent 不在 ddd4j-runtime-guice 聚合注解中");
            passed++;
        }

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
            throw new IllegalStateException("ddd4j-runtime-guice annotation verification failed");
        } else {
            log.info("ALL PASSED! ddd4j-runtime-guice 注解收敛符合架构设计");
        }
    }

    /**
     * \u9a8c\u8bc1 DDD \u6ce8\u89e3\u53ca\u5f53\u524d\u6846\u67b6\u9700\u8981\u7684\u5143\u6ce8\u89e3\u3002
     * @param name \u7528\u4e8e\u8bca\u65ad\u7684\u6ce8\u89e3\u540d\u79f0
     * @param dddAnnotation \u5f85\u9a8c\u8bc1\u7684 DDD \u6ce8\u89e3\u7c7b\u578b
     * @return \u6821\u9a8c\u901a\u8fc7\u8fd4\u56de 1\uff0c\u5931\u8d25\u8fd4\u56de 0
     */
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

}

/** \u63d0\u4f9b\u5e26\u9886\u57df\u670d\u52a1\u6ce8\u89e3\u7684\u793a\u4f8b Bean\uff0c\u7528\u4e8e\u9a8c\u8bc1\u6ce8\u89e3\u6574\u5408\u3002 */
@DomainService
class BusinessDomainService {
    /**
     * \u8fd4\u56de\u6ce8\u89e3\u878d\u5408\u6d4b\u8bd5\u4f7f\u7528\u7684\u670d\u52a1\u63a2\u9488\u7ed3\u679c\u3002
     * @return \u56fa\u5b9a\u7684\u63a2\u9488\u6587\u672c
     */
    public String hello() {
        return "hello";
    }
}
