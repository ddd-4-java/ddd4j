package io.ddd4j.sample.spring.cqrs;

import io.ddd4j.spring.config.SpringCommandConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 复用共享订单业务的独立 Spring 入口，使用 MySQL 四表和原生 Spring 命令总线。
 */
@Import(SpringCommandConfig.class)
@SpringBootApplication(excludeName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
public class SpringCqrsSampleApplication {
    /** @param args Spring Boot \u542f\u52a8\u53c2\u6570 */
    public static void main(String[] args) {
        SpringApplication.run(SpringCqrsSampleApplication.class, args);
    }

}
