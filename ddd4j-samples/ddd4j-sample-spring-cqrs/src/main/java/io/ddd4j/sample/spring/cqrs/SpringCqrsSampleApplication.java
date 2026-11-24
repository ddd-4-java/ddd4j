package io.ddd4j.sample.spring.cqrs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 复用共享订单业务的独立 Spring 入口，采用显式配置的持久化事件存储和 Outbox。 */
@SpringBootApplication
public class SpringCqrsSampleApplication {
    /**
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SpringCqrsSampleApplication.class, args);
    }
}
