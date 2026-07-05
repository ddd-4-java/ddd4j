package io.ddd4j.sample.spring.satoken;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot + Sa-Token 鉴权示例启动类。
 *
 * <p>最推荐的鉴权方案——Sa-Token 轻量、API 友好，与 ddd4j SubjectKit 深度整合。
 *
 * <p>本启动类同时启用 Auth 鉴权（一轨）+ Order 充血业务（二轨）+ Goods CRUD（三轨）三个模块。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "io.ddd4j.sample.spring.satoken",
        "io.ddd4j.core.ddd.repository"
})
public class SpringSaTokenApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringSaTokenApplication.class, args);
    }

}
