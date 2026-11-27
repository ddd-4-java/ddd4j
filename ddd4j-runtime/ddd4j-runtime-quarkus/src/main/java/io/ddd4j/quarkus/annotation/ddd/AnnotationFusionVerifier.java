/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.quarkus.annotation.ddd;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Stereotype;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * 独立验证器：验证 ddd4j-runtime-quarkus 的 DDD 注解与 Jakarta CDI 元注解融合正确。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
@Slf4j
public final class AnnotationFusionVerifier {

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>AnnotationFusionVerifier</code> \u5b9e\u4f8b\u3002 */
    private AnnotationFusionVerifier() {
    }

    /**
     * \u9a8c\u8bc1 Quarkus \u9886\u57df\u6ce8\u89e3\u4e0e CDI \u6ce8\u89e3\u7684\u878d\u5408\u5173\u7cfb\u3002
     * @param args \u547d\u4ee4\u884c\u53c2\u6570
     */
    public static void main(String[] args) {
        int passed = 0;
        int failed = 0;

        passed += verifyStereotype("DomainService", DomainService.class);
        passed += verifyStereotype("DomainRepository", DomainRepository.class);
        passed += verifyStereotype("ApplicationService", ApplicationService.class);
        passed += verifyStereotype("QueryService", QueryService.class);
        passed += verifyStereotype("CommandExecutor", CommandExecutor.class);
        passed += verifyModel("DomainEntity", DomainEntity.class);
        passed += verifyModel("DomainValueObject", DomainValueObject.class);
        passed += verifyStereotype("DomainGateway", DomainGateway.class);
        passed += verifyStereotype("DomainAssembler", DomainAssembler.class);
        passed += verifyStereotype("DomainConverter", DomainConverter.class);

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

    /**
     * \u9a8c\u8bc1\u7ec4\u4ef6\u6ce8\u89e3\u58f0\u660e DDD\u3001CDI \u5e94\u7528\u4f5c\u7528\u57df\u548c\u6784\u4ef6\u7c7b\u578b\u5143\u6ce8\u89e3\u3002
     * @param name \u7528\u4e8e\u8bca\u65ad\u7684\u6ce8\u89e3\u540d\u79f0
     * @param annotationType \u5f85\u9a8c\u8bc1\u7684\u6ce8\u89e3\u7c7b\u578b
     * @return \u6821\u9a8c\u901a\u8fc7\u8fd4\u56de 1\uff0c\u5931\u8d25\u8fd4\u56de 0
     */
    private static int verifyStereotype(String name,
                                        Class<? extends java.lang.annotation.Annotation> annotationType) {
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
        if (Objects.isNull(annotationType.getAnnotation(Stereotype.class))) {
            log.error("FAIL {}: 缺少 @Stereotype 元注解", name);
            return 0;
        }
        log.info("PASS {}: 已融合 @ApplicationScoped", name);
        return 1;
    }

    /**
     * \u9a8c\u8bc1\u9886\u57df\u6a21\u578b\u6ce8\u89e3\u5177\u6709 DDD \u8bed\u4e49\u4e14\u6ca1\u6709\u58f0\u660e CDI \u5e94\u7528\u4f5c\u7528\u57df\u3002
     * @param name \u7528\u4e8e\u8bca\u65ad\u7684\u6ce8\u89e3\u540d\u79f0
     * @param annotationType \u5f85\u9a8c\u8bc1\u7684\u6ce8\u89e3\u7c7b\u578b
     * @return \u6821\u9a8c\u901a\u8fc7\u8fd4\u56de 1\uff0c\u5931\u8d25\u8fd4\u56de 0
     */
    private static int verifyModel(String name,
                                   Class<? extends java.lang.annotation.Annotation> annotationType) {
        if (Objects.isNull(annotationType.getAnnotation(DDDAnnotation.class))) {
            log.error("FAIL {}: 缺少 @DDDAnnotation 元注解", name);
            return 0;
        }
        if (Objects.nonNull(annotationType.getAnnotation(ApplicationScoped.class))) {
            log.error("FAIL {}: 领域模型不应绑定 @ApplicationScoped", name);
            return 0;
        }
        log.info("PASS {}: 保持非 CDI 领域模型语义", name);
        return 1;
    }
}
