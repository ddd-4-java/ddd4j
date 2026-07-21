package io.ddd4j.sample.spring.security;

import io.ddd4j.spring.context.SpringContextBridge;
import io.ddd4j.spring.event.SpringDomainEventPublisher;
import io.ddd4j.auth.security.handler.SecurityExceptionHandler;
import io.ddd4j.web.webmvc.core.GlobalRestExceptionAdvice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot + Spring Security 鉴权示例启动类。
 *
 * <p>演示旧 Spring Security 项目如何通过 SubjectKit 统一鉴权入口，
 * 后续可平滑迁移到 Sa-Token（业务代码零改动）。
 *
 * <p>本启动类同时启用 Auth 鉴权（一轨）+ Order 充血业务（二轨）+ Goods CRUD（三轨）三个模块。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "io.ddd4j.sample.spring.security",
        "io.ddd4j.core.ddd.repository"
})
@Import({SpringContextBridge.class, SpringDomainEventPublisher.class,
        GlobalRestExceptionAdvice.class, SecurityExceptionHandler.class})
public class SpringSecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringSecurityApplication.class, args);
    }

}
