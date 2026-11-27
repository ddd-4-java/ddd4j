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
package io.ddd4j.ddd.cola.rules;

import com.tngtech.archunit.lang.ArchRule;

import java.lang.annotation.Annotation;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * COLA 风格的 DDD 分层纪律 ArchUnit 规则集（注解驱动）。
 *
 * <p>COLA（Clean Object-oriented and Layered Architecture）是阿里推荐的 DDD 落地架构。
 * 本类提供与 ddd4j-ddd-rules-clean 相同的注解驱动规则，但适配 COLA 的命名风格
 * （domain / adapter / application / infrastructure）。
 *
 * <p><b>框架无关性说明</b>：本模块不依赖任何具体注解实现类。注解类作为 {@link Class} 参数
 * 由调用方传入（通常为业务项目使用的某个框架适配注解包中的具体注解）。
 *
 * <h2>COLA vs Clean 命名差异</h2>
 * <ul>
 *   <li>Clean: domain / application / adapter / infrastructure</li>
 *   <li>COLA: domain / adapter / application / infrastructure（注意 adapter 和 application 顺序）</li>
 * </ul>
 *
 * <h3>规则清单</h3>
 * <ul>
 *   <li>{@link #domainEntityInDomain} — @DomainEntity 必须在 domain.model 包</li>
 *   <li>{@link #domainServiceInDomain} — @DomainService 必须在 domain 包</li>
 *   <li>{@link #applicationServiceInApp} — @ApplicationService 必须在 application.executor 包</li>
 *   <li>{@link #repositoryImplInAdapter} — @DomainRepository 必须在 adapter.persistence 包</li>
 *   <li>{@link #DOMAIN_NOT_DEPEND_ON_ADAPTER} — domain 不得依赖 adapter</li>
 *   <li>{@link #DOMAIN_NOT_DEPEND_ON_FRAMEWORK} — domain 不得依赖框架</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class ColaDDDLayerRules {

    /**
     * 规则5：domain 包不得依赖 adapter 包（COLA 核心约束）。
     */
    public static final ArchRule DOMAIN_NOT_DEPEND_ON_ADAPTER = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")
            .because("COLA 核心约束：domain 不得依赖 adapter（依赖方向应反转）");
    /**
     * 规则6：domain 包不得依赖 application / infrastructure 包。
     */
    public static final ArchRule DOMAIN_NOT_DEPEND_ON_APPLICATION = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..app..", "..application..", "..client..", "..infrastructure..", "..start..")
            .because("COLA: domain 不得依赖 application / infrastructure");
    /**
     * 规则7：domain 包不得依赖 Spring/MyBatis 框架。
     */
    public static final ArchRule DOMAIN_NOT_DEPEND_ON_FRAMEWORK = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "com.baomidou..", "org.apache.ibatis..",
                    "jakarta.persistence..", "javax.persistence..", "org.hibernate..",
                    "jakarta.ws.rs..", "javax.ws.rs..", "io.vertx..", "io.javalin..",
                    "io.micronaut..", "jakarta.servlet..", "javax.servlet..")
            .because("领域层不得依赖 Spring/MyBatis/iBatis 等框架");

    /**
     * 私有构造方法，防止实例化工具类。
     */
    private ColaDDDLayerRules() {
    }

    /**
     * \u89c4\u52191\uff1a\u6807\u4e86 {@code @DomainEntity} \u7684\u7c7b\u5fc5\u987b\u5728 {@code ..domain..} \u5305\uff08COLA \u4e2d\u901a\u5e38\u5728 domain.model\uff09\u3002
     *
     * @param domainEntityAnnotation \u9886\u57df\u5b9e\u4f53\u6ce8\u89e3\u7c7b\u578b
     * @return \u9886\u57df\u5b9e\u4f53\u6240\u5c5e\u5305\u89c4\u5219
     */
    public static ArchRule domainEntityInDomain(Class<? extends Annotation> domainEntityAnnotation) {
        return classes()
                .that().areAnnotatedWith(domainEntityAnnotation)
                .should().resideInAPackage("..domain..")
                .because("标了 @DomainEntity 的类是领域实体，必须在 domain 包（COLA 推荐 domain.model）");
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
                .because("标了 @DomainService 的类是领域服务，必须在 domain 包（COLA 推荐 domain.ability）");
    }

    /**
     * \u89c4\u52193\uff1a\u6807\u4e86 {@code @ApplicationService} \u7684\u7c7b\u5fc5\u987b\u5728 application.executor / application.query \u5305\u3002
     *
     *  <p>COLA \u7279\u6709\uff1a\u5e94\u7528\u5c42\u6309 CQS \u5206\u4e3a executor\uff08\u547d\u4ee4\uff09\u548c query\uff08\u67e5\u8be2\uff09\u3002
     *
     * @param applicationServiceAnnotation \u5e94\u7528\u670d\u52a1\u6ce8\u89e3\u7c7b\u578b
     * @return \u5e94\u7528\u670d\u52a1\u6240\u5c5e\u5305\u89c4\u5219
     */
    public static ArchRule applicationServiceInApp(Class<? extends Annotation> applicationServiceAnnotation) {
        return classes()
                .that().areAnnotatedWith(applicationServiceAnnotation)
                .should().resideInAnyPackage(
                        "..application.executor..", "..application.query..", "..app..", "..application..")
                .because("标了 @ApplicationService 的类必须在 application.executor / application.query（COLA CQS 分离）");
    }

    /**
     * \u89c4\u52194\uff1a\u6807\u4e86 {@code @DomainRepository} \u7684\u7c7b\u5fc5\u987b\u5728 {@code ..adapter.persistence..} \u5305\u3002
     *
     *  <p>COLA \u4e2d Repository \u5b9e\u73b0\u653e\u5728 adapter \u5c42\uff08Clean \u4e2d\u653e\u5728 infrastructure \u5c42\uff09\u3002
     *
     * @param domainRepositoryAnnotation \u4ed3\u50a8\u5b9e\u73b0\u6ce8\u89e3\u7c7b\u578b
     * @return \u4ed3\u50a8\u5b9e\u73b0\u6240\u5c5e\u5305\u89c4\u5219
     */
    public static ArchRule repositoryImplInAdapter(Class<? extends Annotation> domainRepositoryAnnotation) {
        return classes()
                .that().areAnnotatedWith(domainRepositoryAnnotation)
                .should().resideInAnyPackage("..infrastructure.persistence..", "..adapter.persistence..")
                .because("COLA: 仓储实现应位于持久化适配包");
    }

    /**
     * \u89c4\u52198\uff1a\u6807\u4e86 {@code @DomainGateway} \u7684\u63a5\u53e3\u5fc5\u987b\u5728 {@code ..domain.gateway..} \u5305\u3002
     *
     * @param domainGatewayAnnotation \u9886\u57df\u7f51\u5173\u6ce8\u89e3\u7c7b\u578b
     * @return \u9886\u57df\u7f51\u5173\u6240\u5c5e\u5305\u89c4\u5219
     */
    public static ArchRule domainGatewayInDomain(Class<? extends Annotation> domainGatewayAnnotation) {
        return classes()
                .that().areAnnotatedWith(domainGatewayAnnotation)
                .should().resideInAPackage("..domain..")
                .because("标了 @DomainGateway 的接口是领域网关，必须在 domain.gateway 包");
    }

    /**
     * \u89c4\u52199\uff1a\u6807\u4e86 {@code @CommandExecutor} \u7684\u7c7b\u5fc5\u987b\u5728 {@code ..application.executor..} \u5305\u3002
     *
     * @param commandExecutorAnnotation \u547d\u4ee4\u6267\u884c\u5668\u6ce8\u89e3\u7c7b\u578b
     * @return \u547d\u4ee4\u6267\u884c\u5668\u6240\u5c5e\u5305\u89c4\u5219
     */
    public static ArchRule commandExecutorInApp(Class<? extends Annotation> commandExecutorAnnotation) {
        return classes()
                .that().areAnnotatedWith(commandExecutorAnnotation)
                .should().resideInAnyPackage("..app.executor..", "..application.executor..", "..application..")
                .because("标了 @CommandExecutor 的类必须在 application.executor（COLA CQS 写侧）");
    }

    /**
     * \u89c4\u521910\uff1a\u6807\u4e86 {@code @QueryService} \u7684\u7c7b\u5fc5\u987b\u5728 {@code ..application.query..} \u5305\u3002
     *
     * @param queryServiceAnnotation \u67e5\u8be2\u670d\u52a1\u6ce8\u89e3\u7c7b\u578b
     * @return \u67e5\u8be2\u670d\u52a1\u6240\u5c5e\u5305\u89c4\u5219
     */
    public static ArchRule queryServiceInApp(Class<? extends Annotation> queryServiceAnnotation) {
        return classes()
                .that().areAnnotatedWith(queryServiceAnnotation)
                .should().resideInAnyPackage("..app.query..", "..application.query..", "..application..")
                .because("标了 @QueryService 的类必须在 application.query（COLA CQS 读侧）");
    }
}
