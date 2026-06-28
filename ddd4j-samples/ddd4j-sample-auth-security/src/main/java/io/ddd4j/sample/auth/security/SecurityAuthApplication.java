package io.ddd4j.sample.auth.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ddd4j-auth + Spring Security 示例启动类。
 *
 * <p>演示旧 Spring Security 项目如何通过 SubjectKit 统一鉴权入口，
 * 后续可平滑迁移到 sa-token（业务代码零改动）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@SpringBootApplication
public class SecurityAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityAuthApplication.class, args);
    }

}
