package io.ddd4j.sample.auth.shiro.config;

import io.ddd4j.core.subject.AuthPrincipal;
import io.ddd4j.core.subject.SubjectDataProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 鉴权配置：注册权限数据源（SubjectDataProvider）。
 *
 * <p>与 sa-token 示例完全一致的配置——切换底层鉴权框架时数据源零改动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration
public class AuthConfig {

    @Bean
    public SubjectDataProvider subjectDataProvider() {
        return new SubjectDataProvider() {
            @Override
            public List<String> getPermissionList(AuthPrincipal principal) {
                if ("10001".equals(String.valueOf(principal.getLoginId()))) {
                    return List.of("user:add", "user:delete", "user:list");
                }
                return List.of("user:list");
            }

            @Override
            public List<String> getRoleList(AuthPrincipal principal) {
                if ("10001".equals(String.valueOf(principal.getLoginId()))) {
                    return List.of("admin", "user");
                }
                return List.of("user");
            }
        };
    }

}
