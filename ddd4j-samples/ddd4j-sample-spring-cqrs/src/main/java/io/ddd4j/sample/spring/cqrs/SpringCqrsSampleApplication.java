package io.ddd4j.sample.spring.cqrs;

import io.ddd4j.spring.config.SpringCommandConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 复用共享订单业务的独立 Spring 入口，使用 MySQL 四表和原生 Spring 命令总线。
 */
@Import(SpringCommandConfig.class)
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class SpringCqrsSampleApplication {
    /**
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SpringCqrsSampleApplication.class, args);
    }

}
