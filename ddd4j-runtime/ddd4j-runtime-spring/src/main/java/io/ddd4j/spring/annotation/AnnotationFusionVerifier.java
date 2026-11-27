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

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>AnnotationFusionVerifier</code> \u5b9e\u4f8b\u3002 */
    private AnnotationFusionVerifier() {
    }

    /**
     * main() 方法。
     * @param args 命令行参数。
     */
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

    /**
     * \u9a8c\u8bc1 DDD \u6ce8\u89e3\u53ca\u5f53\u524d\u6846\u67b6\u9700\u8981\u7684\u5143\u6ce8\u89e3\u3002
     * @param name \u7528\u4e8e\u8bca\u65ad\u7684\u6ce8\u89e3\u540d\u79f0
     * @param annotationType \u5f85\u9a8c\u8bc1\u7684\u6ce8\u89e3\u7c7b\u578b
     * @param springAnnotationType \u7ec4\u4ef6\u8981\u6c42\u5305\u542b\u7684 Spring \u5143\u6ce8\u89e3
     * @return \u6821\u9a8c\u901a\u8fc7\u8fd4\u56de 1\uff0c\u5931\u8d25\u8fd4\u56de 0
     */
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
