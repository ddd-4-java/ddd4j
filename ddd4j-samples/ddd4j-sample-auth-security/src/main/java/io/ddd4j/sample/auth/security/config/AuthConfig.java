package io.ddd4j.sample.auth.security.config;

import io.ddd4j.core.subject.AuthPrincipal;
import io.ddd4j.core.subject.SubjectDataProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * 鉴权配置：注册权限数据源 + Spring Security 放行规则。
 *
 * <p>权限数据源（SubjectDataProvider）与 sa-token/shiro 示例完全一致，
 * 证明切换底层鉴权框架时数据源零改动。
 *
 * <p>Spring Security 的 FilterChain 放行所有请求到 Controller，
 * 实际权限校验通过 SubjectKit（委托 SubjectDataProvider）完成。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration
public class AuthConfig {

    /**
     * 注册权限数据源。
     */
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

    /**
     * Spring Security 放行规则。
     * <p>本示例通过 SubjectKit 做权限校验，Spring Security 仅做请求放行。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

}
