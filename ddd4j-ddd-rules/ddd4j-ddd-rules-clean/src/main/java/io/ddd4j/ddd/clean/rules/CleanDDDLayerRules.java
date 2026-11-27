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
package io.ddd4j.ddd.clean.rules;

import com.tngtech.archunit.lang.ArchRule;

import java.lang.annotation.Annotation;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Clean Architecture 风格的 DDD 分层纪律 ArchUnit 规则集（注解驱动）。
 *
 * <p>从 ddd4j-core 的 DDDLayerRules 迁出，按 Clean Architecture 命名（domain/application/adapter/infrastructure）。
 * 业务项目可继承 {@link io.ddd4j.ddd.clean.test.CleanArchitectureTest} 自动应用所有规则，
 * 或直接引用这些 {@link ArchRule} 常量自定义检查。
 *
 * <p>规则基于 ddd4j 的注解体系（{@code @DomainEntity} / {@code @DomainService} /
 * {@code @ApplicationService} / {@code @DomainRepository}），不绑定具体的包名，
 * 而是通过注解标记校验——这样无论业务项目用什么包结构，只要正确标注了注解就能被校验。
 *
 * <p><b>框架无关性说明</b>：本模块不依赖任何具体注解实现类。注解类作为 {@link Class} 参数
 * 由调用方传入，通常为业务项目使用的某个框架适配注解包中的具体注解：
 * <ul>
 *   <li>Spring Boot 业务：{@code io.ddd4j.spring.annotation.DomainService}</li>
 *   <li>Quarkus 业务：{@code io.ddd4j.quarkus.annotation.ddd.DomainService}</li>
 *   <li>Javalin 业务：{@code io.ddd4j.javalin.annotation.DomainService}</li>
 * </ul>
 *
 * <h2>规则清单</h2>
 * <ul>
 *   <li>{@link #domainEntityInDomain(Class)} — @DomainEntity 标记的类必须在 domain 包</li>
 *   <li>{@link #domainServiceInDomain(Class)} — @DomainService 标记的类必须在 domain 包</li>
 *   <li>{@link #applicationServiceInApp(Class)} — @ApplicationService 标记的类必须在 app 包</li>
 *   <li>{@link #repositoryImplInInfrastructure(Class)} — @DomainRepository 标记的类必须在 infrastructure 包</li>
 *   <li>{@link #DOMAIN_NOT_DEPEND_ON_WEB} — domain 包不得依赖 web/controller/adapter 包</li>
 *   <li>{@link #DOMAIN_NOT_DEPEND_ON_INFRASTRUCTURE} — domain 包不得依赖 infrastructure 包</li>
 *   <li>{@link #DOMAIN_NOT_DEPEND_ON_FRAMEWORK} — domain 包不得依赖 Spring/MyBatis 等框架</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class CleanDDDLayerRules {

    /**
     * 规则5：domain 包不得依赖 web/controller/adapter 包。
     */
    public static final ArchRule DOMAIN_NOT_DEPEND_ON_WEB = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..controller..", "..adapter..", "..web..")
            .because("领域层不得依赖 Web/Controller/Adapter 层");
    /**
     * 规则6：domain 包不得依赖 infrastructure 包。
     */
    public static final ArchRule DOMAIN_NOT_DEPEND_ON_INFRASTRUCTURE = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure..", "..infras..")
            .because("领域层不得依赖基础设施层（依赖方向应反转）");
    /**
     * 规则7：domain 包不得依赖 Spring 框架。
     */
    public static final ArchRule DOMAIN_NOT_DEPEND_ON_FRAMEWORK = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "com.baomidou..", "org.apache.ibatis..")
            .because("领域层不得依赖 Spring/MyBatis/iBatis 等框架");

    /**
     * 私有构造方法，防止实例化工具类。
     */
    private CleanDDDLayerRules() {
    }

    /**
     * \u89c4\u52191\uff1a\u6807\u4e86 {@code @DomainEntity} \u7684\u7c7b\u5fc5\u987b\u5728 {@code ..domain..} \u5305\u3002
     *
     * @param domainEntityAnnotation \u9886\u57df\u5b9e\u4f53\u6ce8\u89e3\u7c7b\u578b
     * @return \u9886\u57df\u5b9e\u4f53\u6240\u5c5e\u5305\u89c4\u5219
     */
    public static ArchRule domainEntityInDomain(Class<? extends Annotation> domainEntityAnnotation) {
        return classes()
                .that().areAnnotatedWith(domainEntityAnnotation)
                .should().resideInAPackage("..domain..")
                .because("标了 @DomainEntity 的类是领域实体，必须在 domain 包");
    }

    /**
     * \u89c4\u52192\uff1a\u6807\u4e86 {@code @DomainService} \u7684\u7c7b\u5fc5\u987b\u5728 {@code ..domain..} \u5305\u3002
     *
     * @param domainServiceAnnotation \u9886\u57df\u670d\u52a1\u6ce8\u89e3\u7c7b\u578b
     * @return \u9886\u57df\u670d\u52a1\u6240\u5c5e\u5305\u89c4\u5219
     */
    public static ArchRule domainServiceInDomain(Class<? extends Annotation> domainServiceAnnotation) {
        return classes()
                .that().areAnnotatedWith(domainServiceAnnotation)
                .should().resideInAPackage("..domain..")
                .because("标了 @DomainService 的类是领域服务，必须在 domain 包");
    }

    /**
     * \u89c4\u52193\uff1a\u6807\u4e86 {@code @ApplicationService} \u7684\u7c7b\u5fc5\u987b\u5728 {@code ..app..} \u6216 {@code ..application..} \u5305\u3002
     *
     * @param applicationServiceAnnotation \u5e94\u7528\u670d\u52a1\u6ce8\u89e3\u7c7b\u578b
     * @return \u5e94\u7528\u670d\u52a1\u6240\u5c5e\u5305\u89c4\u5219
     */
    public static ArchRule applicationServiceInApp(Class<? extends Annotation> applicationServiceAnnotation) {
        return classes()
                .that().areAnnotatedWith(applicationServiceAnnotation)
                .should().resideInAnyPackage("..app..", "..application..")
                .because("标了 @ApplicationService 的类是应用服务，必须在 app/application 包");
    }

    /**
     * \u89c4\u52194\uff1a\u6807\u4e86 {@code @DomainRepository} \u7684\u7c7b\u5fc5\u987b\u5728 {@code ..infrastructure..} \u6216 {@code ..infras..} \u5305\u3002
     *
     * @param domainRepositoryAnnotation \u4ed3\u50a8\u5b9e\u73b0\u6ce8\u89e3\u7c7b\u578b
     * @return \u4ed3\u50a8\u5b9e\u73b0\u6240\u5c5e\u5305\u89c4\u5219
     */
    public static ArchRule repositoryImplInInfrastructure(Class<? extends Annotation> domainRepositoryAnnotation) {
        return classes()
                .that().areAnnotatedWith(domainRepositoryAnnotation)
                .should().resideInAnyPackage("..infrastructure..", "..infras..")
                .because("标了 @DomainRepository 的类是仓储实现，必须在 infrastructure/infras 包");
    }

    /**
     * \u89c4\u52198\uff1a\u6807\u4e86 {@code @DomainGateway} \u7684\u63a5\u53e3\u5fc5\u987b\u5728 {@code ..domain..} \u5305\u3002
     *
     * @param domainGatewayAnnotation \u9886\u57df\u7f51\u5173\u6ce8\u89e3\u7c7b\u578b
     * @return \u9886\u57df\u7f51\u5173\u6240\u5c5e\u5305\u89c4\u5219
     */
    public static ArchRule domainGatewayInDomain(Class<? extends Annotation> domainGatewayAnnotation) {
        return classes()
                .that().areAnnotatedWith(domainGatewayAnnotation)
                .should().resideInAPackage("..domain..")
                .because("标了 @DomainGateway 的接口是领域网关，必须在 domain 包");
    }

    /**
     * \u89c4\u52199\uff1a\u6807\u4e86 {@code @CommandExecutor} \u7684\u7c7b\u5fc5\u987b\u5728 {@code ..app..} \u6216 {@code ..application..} \u5305\u3002
     *
     * @param commandExecutorAnnotation \u547d\u4ee4\u6267\u884c\u5668\u6ce8\u89e3\u7c7b\u578b
     * @return \u547d\u4ee4\u6267\u884c\u5668\u6240\u5c5e\u5305\u89c4\u5219
     */
    public static ArchRule commandExecutorInApp(Class<? extends Annotation> commandExecutorAnnotation) {
        return classes()
                .that().areAnnotatedWith(commandExecutorAnnotation)
                .should().resideInAnyPackage("..app..", "..application..")
                .because("标了 @CommandExecutor 的类必须在 app/application 包");
    }

    /**
     * \u89c4\u521910\uff1a\u6807\u4e86 {@code @QueryService} \u7684\u7c7b\u5fc5\u987b\u5728 {@code ..app..} \u6216 {@code ..application..} \u5305\u3002
     *
     * @param queryServiceAnnotation \u67e5\u8be2\u670d\u52a1\u6ce8\u89e3\u7c7b\u578b
     * @return \u67e5\u8be2\u670d\u52a1\u6240\u5c5e\u5305\u89c4\u5219
     */
    public static ArchRule queryServiceInApp(Class<? extends Annotation> queryServiceAnnotation) {
        return classes()
                .that().areAnnotatedWith(queryServiceAnnotation)
                .should().resideInAnyPackage("..app..", "..application..")
                .because("标了 @QueryService 的类必须在 app/application 包");
    }
}
