package io.ddd4j.sample.spring.shiro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot + Apache Shiro 鉴权示例启动类。
 *
 * <p>演示旧 Shiro 项目如何通过 SubjectKit 统一鉴权入口，
 * 后续可平滑迁移到 Sa-Token（业务代码零改动）。
 *
 * <p>本启动类同时启用 Auth 鉴权（一轨）+ Order 充血业务（二轨）+ Goods CRUD（三轨）三个模块。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "io.ddd4j.sample.spring.shiro",
        "io.ddd4j.core.ddd.repository"
})
public class SpringShiroApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringShiroApplication.class, args);
    }

}
