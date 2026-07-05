package io.ddd4j.sample.quarkus.satoken.config;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.subject.SubjectDataProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * 鉴权配置：注册权限数据源（SubjectDataProvider）。
 *
 * <p>Quarkus 使用 CDI 注解（@Produces / @ApplicationScoped）代替 Spring 的 @Bean，
 * 但 SubjectDataProvider 的实现逻辑与 Spring / Javalin 示例完全一致。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class AuthConfig {

    /**
     * 注册权限数据源。
     * <p>实际项目中从数据库/缓存查询，此处用 Mock 数据演示。
     */
    @Produces
    @Singleton
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
