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
    /** @param args Spring Boot \u542f\u52a8\u53c2\u6570 */
    public static void main(String[] args) {
        SpringApplication.run(SpringCqrsSampleApplication.class, args);
    }

    /**
     * \u6ce8\u518c\u4ece Spring \u5bb9\u5668\u52a0\u8f7d\u547d\u4ee4\u6267\u884c\u5668\u7684\u5546\u54c1\u4ea4\u6613\u547d\u4ee4\u603b\u7ebf\u3002
     * @param context \u63d0\u4f9b\u547d\u4ee4\u6267\u884c\u5668\u7684 Spring \u5e94\u7528\u4e0a\u4e0b\u6587
     * @return \u5f53\u524d\u5e94\u7528\u4f7f\u7528\u7684\u547d\u4ee4\u603b\u7ebf
     */
    @Bean
    public SpringCommandBus springCommandBus(ApplicationContext context) {
        return new SpringCommandBus(context);
    }
}
