package io.ddd4j.sample.spring.security.config;

import io.ddd4j.sample.spring.security.rbac.InMemoryRoleRepository;
import io.ddd4j.sample.spring.security.rbac.InMemoryUserRepository;
import io.ddd4j.sample.spring.security.rbac.Role;
import io.ddd4j.sample.spring.security.rbac.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Spring Security 配置：HTTP 安全 + UserDetailsService + 启用方法级注解权限。
 *
 * <p>关键差异（对比 Sa-Token / Shiro 示例）：
 * <ul>
 *   <li>{@link EnableMethodSecurity}：启用 {@code @PreAuthorize} / {@code @PostAuthorize} / {@code @Secured}</li>
 *   <li>{@link UserDetailsService}：把 RBAC 用户/角色桥接到 Spring Security 的 {@code Authentication}</li>
 *   <li>{@link PasswordEncoder}：演示用 {@link NoOpPasswordEncoder}，生产应使用 BCrypt</li>
 *   <li>{@link SecurityFilterChain}：CSRF 关闭 + 放行所有请求 + 启用 HTTP Basic（仅供演示）</li>
 * </ul>
 *
 * <p>权限数据源（{@code SubjectDataProvider}）注册仍在 {@link RbacConfig}，
 * 让 ddd4j 的 Subject 抽象与 Spring Security 解耦——业务代码切换鉴权框架时无需改动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 桥接 RBAC 用户仓储 ⇢ Spring Security UserDetailsService。
     * <p>角色（{@code ROLE_xxx}）从 RBAC 仓储读取；权限（{@code xxx:yyy}）由
     * {@link RbacConfig#subjectDataProvider()} 通过 ddd4j 抽象提供。
     */
    @Bean
    public UserDetailsService userDetailsService(InMemoryUserRepository userRepository,
                                                 InMemoryRoleRepository roleRepository) {
        return username -> {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("user not found: " + username));
            // 角色：ROLE_xxx（Spring Security 默认前缀）
            List<SimpleGrantedAuthority> authorities = roleRepository.findAll().stream()
                    .filter(r -> user.getRoleCodes().contains(r.getCode()))
                    .map(Role::getCode)
                    .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                    .toList();
            // 权限：直接放入 authorities，Spring Security 的 hasAuthority() 会匹配
            // （合并角色+权限，统一作为 authorities）
            return new org.springframework.security.core.userdetails.User(
                    user.getUsername(),
                    user.getPassword(),
                    user.isEnabled(),
                    true,
                    true,
                    true,
                    authorities
            );
        };
    }

    /**
     * 密码编码器：演示用 NoOp（明文比对）。
     * <p>生产环境应使用 {@code BCryptPasswordEncoder}。
     */
    @SuppressWarnings("deprecation")
    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }

    /**
     * HTTP 安全配置。
     * <ul>
     *   <li>CSRF 关闭（演示用）</li>
     *   <li>HTTP Basic 启用（便于 curl/Postman 测试）</li>
     *   <li>所有请求放行——方法级 {@code @PreAuthorize} 控制实际权限</li>
     * </ul>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(basic -> {
                })
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

}