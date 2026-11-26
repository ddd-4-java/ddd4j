package io.ddd4j.sample.spring.cqrs;

import io.ddd4j.spring.command.SpringCommandBus;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * 复用共享订单业务的独立 Spring 入口，使用 MySQL 四表和原生 Spring 命令总线。
 */
@SpringBootApplication(excludeName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
public class SpringCqrsSampleApplication {
    /**
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SpringCqrsSampleApplication.class, args);
    }

    @Bean
    public SpringCommandBus springCommandBus(ApplicationContext context) {
        return new SpringCommandBus(context);
    }
}
