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
package io.ddd4j.quarkus.extension;

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
 * <p>
 * 实现 {@link Extension} 接口，在 CDI 容器处理注解类型时识别 DDD 构造型注解
 * （如 {@link DomainService}、{@link ApplicationService} 等），
 * 自动为标注这些注解的类添加 {@link ApplicationScoped} 作用域。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class DddCdiExtension implements Extension {

    /**
     * \u68c0\u67e5\u4f7f\u7528 DDD \u7ec4\u4ef6\u6ce8\u89e3\u7684\u7c7b\u578b\u5e76\u8865\u5145\u9002\u7528\u7684 CDI \u5143\u6570\u636e\u3002
     * @param <T> \u5f53\u524d\u5bb9\u5668\u5904\u7406\u7684\u7ec4\u4ef6\u6216\u4e8b\u4ef6\u7c7b\u578b
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
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

    /**
     * \u627e\u51fa\u5f85\u5904\u7406\u7c7b\u578b\u4e0a\u53ef\u8bc6\u522b\u7684 DDD \u6ce8\u89e3\u3002
     * @param <T> \u5f53\u524d\u5bb9\u5668\u5904\u7406\u7684\u7ec4\u4ef6\u6216\u4e8b\u4ef6\u7c7b\u578b
     * @param type \u8981\u89e3\u6790\u7684 Bean \u7c7b\u578b\u6216 CDI \u7c7b\u578b\u4fe1\u606f
     * @return \u5df2\u8bc6\u522b\u7684 DDD \u6ce8\u89e3\u540d\uff0c\u6ca1\u6709\u65f6\u8fd4\u56de\u7a7a
     */
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

    /** \u4e3a\u52a8\u6001\u6ce8\u518c\u7684 CDI Bean \u63d0\u4f9b\u5e94\u7528\u7ea7\u4f5c\u7528\u57df\u6ce8\u89e3\u5b9e\u4f8b\u3002 */
    private static class ApplicationScopedLiteral extends jakarta.enterprise.util.AnnotationLiteral<ApplicationScoped>
            implements ApplicationScoped {

        /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 1L;
    }
}
