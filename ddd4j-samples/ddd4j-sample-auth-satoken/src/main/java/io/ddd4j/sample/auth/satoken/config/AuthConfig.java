package io.ddd4j.sample.auth.satoken.config;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.subject.SubjectDataProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 鉴权配置：注册权限数据源（SubjectDataProvider）。
 *
 * <p>这是 ddd4j-auth 的核心用法——业务实现 SubjectDataProvider 提供权限/角色数据源，
 * 框架不持有权限数据，保证三鉴权行为一致。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration
public class AuthConfig {

    /**
     * 注册权限数据源。
     * <p>实际项目中从数据库/缓存查询，此处用 Mock 数据演示。
     */
    @Bean
    public SubjectDataProvider subjectDataProvider() {
        return new SubjectDataProvider() {
            @Override
            public List<String> getPermissionList(AuthPrincipal principal) {
                // 实际：return permissionService.findByUserId(principal.getUserId());
                if ("10001".equals(String.valueOf(principal.getLoginId()))) {
                    return List.of("user:add", "user:delete", "user:list");
                }
                return List.of("user:list");
            }

            @Override
            public List<String> getRoleList(AuthPrincipal principal) {
                // 实际：return roleService.findByUserId(principal.getUserId());
                if ("10001".equals(String.valueOf(principal.getLoginId()))) {
                    return List.of("admin", "user");
                }
                return List.of("user");
            }
        };
    }

}
