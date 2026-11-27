package io.ddd4j.sample.spring.shiro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 复用共享订单业务的独立 Spring 入口，默认采用明确的单进程演示存储。 */
@SpringBootApplication
public class SpringShiroSampleApplication {
    /** @param args Spring Boot \u542f\u52a8\u53c2\u6570 */
    public static void main(String[] args) {
        SpringApplication.run(SpringShiroSampleApplication.class, args);
    }
}
