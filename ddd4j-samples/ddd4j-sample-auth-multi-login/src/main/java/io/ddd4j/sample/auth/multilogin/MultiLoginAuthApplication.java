package io.ddd4j.sample.auth.multilogin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 多登录场景示例。
 *
 * <p>该示例刻意不复制旧 OAuth2 授权服务器，只展示业务侧如何通过
 * SubjectKit/AuthRequest/Subject SPI 表达手机号登录、第三方登录与登录事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@SpringBootApplication
public class MultiLoginAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultiLoginAuthApplication.class, args);
    }
}
