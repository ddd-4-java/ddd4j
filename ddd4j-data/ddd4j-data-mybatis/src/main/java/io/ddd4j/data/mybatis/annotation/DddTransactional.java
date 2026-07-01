package io.ddd4j.data.mybatis.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ddd4j 事务注解（替代 {@code @Transactional}，保持框架无关）。
 *
 * <p>标注于 {@link io.ddd4j.data.mybatis.repository.impl.BaseRepositoryImpl} 子类的
 * 写方法（save/update/delete），表示该方法需要在事务中执行。
 *
 * <p><b>框架无关设计</b>：本注解定义在 {@code ddd4j-data-mybatis}（不依赖 Spring），
 * 由各框架适配层（{@code ddd4j-adapter-spring} / {@code ddd4j-adapter-quarkus} 等）通过
 * AOP / Interceptor Binding 识别并代理事务。
 *
 * <p><b>Spring 用法</b>：业务项目引入 {@code ddd4j-boot-spring-aspect} 后，
 * 该模块的 AOP 切面会扫描 {@code @DddTransactional} 注解，自动开启事务。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DddTransactional {

    /**
     * 触发回滚的异常类型（默认所有 Exception）。
     */
    Class<? extends Throwable>[] rollbackFor() default {Exception.class};
}
