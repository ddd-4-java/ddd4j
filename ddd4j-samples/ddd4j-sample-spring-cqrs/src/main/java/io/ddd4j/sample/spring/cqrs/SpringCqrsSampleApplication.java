package io.ddd4j.sample.spring.cqrs;

import io.ddd4j.spring.command.SpringCommandBus;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * 复用共享订单业务的独立 Spring 入口，使用 MySQL 四表和原生 Spring 命令总线。
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class SpringCqrsSampleApplication {
    /**
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SpringCqrsSampleApplication.class, args);
    }

    /**
     * 注册从 Spring 容器加载命令执行器的商品交易命令总线。
     *
     * @param context 提供命令执行器的 Spring 应用上下文
     * @return 当前应用使用的命令总线
     */
    @Bean
    public SpringCommandBus springCommandBus(ApplicationContext context) {
        return new SpringCommandBus(context);
    }
}
